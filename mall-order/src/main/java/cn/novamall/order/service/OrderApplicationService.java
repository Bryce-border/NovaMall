package cn.novamall.order.service;

import cn.novamall.common.id.IdGenerator;
import cn.novamall.order.OrderApiModels;
import cn.novamall.order.client.InventoryClient;
import cn.novamall.order.client.ProductClient;
import cn.novamall.order.entity.OrderEntity;
import cn.novamall.order.entity.OrderItemEntity;
import cn.novamall.order.mapper.OrderItemMapper;
import cn.novamall.order.mapper.OrderMapper;
import cn.novamall.order.mapper.OrderAddressMapper;
import cn.novamall.order.entity.OrderAddressEntity;
import cn.novamall.order.client.UserClient;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

@Service
public class OrderApplicationService {

    private static final Logger log = LoggerFactory.getLogger(OrderApplicationService.class);
    private static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    private static final String PAID = "PAID";
    private static final String SHIPPED = "SHIPPED";
    private static final String COMPLETED = "COMPLETED";

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductClient productClient;
    private final InventoryClient inventoryClient;
    private final OrderOutboxService orderOutboxService;
    private final OrderAddressMapper orderAddressMapper;
    private final UserClient userClient;
    private final ObjectMapper json;
    private final OrderRecordService orderRecords;
    private final CouponService coupons;

    public OrderApplicationService(OrderMapper orderMapper, OrderItemMapper orderItemMapper,
                                   ProductClient productClient, InventoryClient inventoryClient,
                                   OrderOutboxService orderOutboxService, OrderAddressMapper orderAddressMapper,
                                   UserClient userClient, ObjectMapper json, OrderRecordService orderRecords, CouponService coupons) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productClient = productClient;
        this.inventoryClient = inventoryClient;
        this.orderOutboxService = orderOutboxService;
        this.orderAddressMapper = orderAddressMapper;
        this.userClient = userClient;
        this.json = json;
        this.orderRecords = orderRecords;
        this.coupons = coupons;
    }

    @Transactional
    public OrderApiModels.OrderResponse create(Long userId, String userName,
                                               OrderApiModels.CreateOrderRequest request) {
        String orderNo = String.valueOf(IdGenerator.nextId());
        UserClient.AddressSnapshot address = loadAddress(userId, request.addressId());
        Map<Long, CheckoutLine> lines = resolveItems(request.items());
        List<Long> attemptedSkuIds = new ArrayList<>();
        boolean synchronizedTransaction = TransactionSynchronizationManager.isSynchronizationActive();
        if (synchronizedTransaction) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) releaseReservations(orderNo, attemptedSkuIds);
                }
            });
        }
        try {
            List<OrderItemEntity> orderItems = new ArrayList<>();
            BigDecimal totalAmount = BigDecimal.ZERO;
            for (CheckoutLine line : lines.values()) {
                ProductClient.SkuSnapshot sku = line.sku();
                // A timeout can follow a committed remote reservation, so compensate every attempt.
                attemptedSkuIds.add(sku.skuId());
                InventoryClient.ReserveResponse reservation = inventoryClient.reserve(
                        new InventoryClient.ReserveRequest(sku.productId(), sku.skuId(), line.quantity(), orderNo));
                if (reservation == null || !reservation.success()) {
                    throw new IllegalArgumentException("inventory reservation failed: "
                            + (reservation == null ? "empty response" : reservation.message()));
                }
                BigDecimal amount = sku.price().multiply(BigDecimal.valueOf(line.quantity()));
                OrderItemEntity item = new OrderItemEntity();
                item.setProductId(sku.productId());
                item.setSkuId(sku.skuId());
                item.setProductName(sku.productName());
                item.setCoverImageUrl(sku.coverImageUrl());
                item.setSkuName(sku.skuName());
                item.setSpecJson(specJson(sku));
                item.setUnitPrice(sku.price());
                item.setQuantity(line.quantity());
                item.setAmount(amount);
                orderItems.add(item);
                totalAmount = totalAmount.add(amount);
            }

            LocalDateTime now = LocalDateTime.now();
            OrderEntity order = new OrderEntity();
            order.setId(IdGenerator.nextId());
            order.setOrderNo(orderNo);
            order.setUserId(userId);
            order.setUserName(userName);
            order.setProductAmount(totalAmount);
            BigDecimal discount = request.couponId() == null ? BigDecimal.ZERO
                    : coupons.reserve(userId, request.couponId(), orderNo, totalAmount);
            order.setDiscountAmount(discount);
            order.setFreightAmount(BigDecimal.ZERO);
            order.setTotalAmount(totalAmount.subtract(discount));
            order.setStatus(PENDING_PAYMENT);
            order.setCreatedAt(now);
            order.setUpdatedAt(now);
            orderMapper.insert(order);
            if (address != null) {
                OrderAddressEntity snapshot = new OrderAddressEntity();
                snapshot.setId(IdGenerator.nextId());
                snapshot.setOrderId(order.getId());
                snapshot.setReceiverName(address.receiverName());
                snapshot.setReceiverPhone(address.receiverPhone());
                snapshot.setProvince(address.province());
                snapshot.setCity(address.city());
                snapshot.setDistrict(address.district());
                snapshot.setDetailAddress(address.detailAddress());
                snapshot.setPostalCode(address.postalCode());
                orderAddressMapper.insert(snapshot);
            }
            for (OrderItemEntity item : orderItems) {
                item.setId(IdGenerator.nextId());
                item.setOrderId(order.getId());
                item.setOrderNo(orderNo);
                orderItemMapper.insert(item);
            }
            orderRecords.created(order);
            orderOutboxService.appendOrderEvent("ORDER_CREATED", order);
            return toResponse(order, orderItems);
        } catch (RuntimeException ex) {
            if (!synchronizedTransaction) releaseReservations(orderNo, attemptedSkuIds);
            throw ex;
        }
    }

    public OrderApiModels.OrderResponse get(Long userId, String orderNo) {
        OrderEntity order = orderMapper.selectOne(Wrappers.<OrderEntity>lambdaQuery()
                .eq(OrderEntity::getUserId, userId)
                .eq(OrderEntity::getOrderNo, orderNo));
        if (order == null) {
            throw new IllegalArgumentException("order not found: " + orderNo);
        }
        List<OrderItemEntity> items = orderItemMapper.selectList(Wrappers.<OrderItemEntity>lambdaQuery()
                .eq(OrderItemEntity::getOrderNo, orderNo));
        return toResponse(order, items);
    }

    public OrderApiModels.OrderPageResponse page(Long userId, long page, long size, String status) {
        Page<OrderEntity> result = orderMapper.findPageByUser(userId, status, page, size);
        List<OrderApiModels.OrderSummaryResponse> summaries = result.getRecords().stream().map(order -> {
            List<OrderItemEntity> items = orderItemMapper.selectList(Wrappers.<OrderItemEntity>lambdaQuery()
                    .eq(OrderItemEntity::getOrderNo, order.getOrderNo()).orderByAsc(OrderItemEntity::getId));
            OrderApiModels.OrderItemResponse first = items.isEmpty() ? null : toItemResponse(items.get(0));
            return new OrderApiModels.OrderSummaryResponse(order.getOrderNo(), order.getStatus(), items.size(),
                    first, order.getTotalAmount(), order.getCreatedAt());
        }).toList();
        return new OrderApiModels.OrderPageResponse(summaries, page, size, result.getTotal(),
                page * size < result.getTotal());
    }

    @Transactional
    public OrderApiModels.OrderResponse cancel(Long userId, String orderNo) {
        OrderEntity order = requireOrderForUpdate(userId, orderNo);
        if ("CANCELLED".equals(order.getStatus())) return loadOrderResponse(order);
        if (!PENDING_PAYMENT.equals(order.getStatus())) {
            throw new IllegalArgumentException("order cannot be cancelled in status: " + order.getStatus());
        }
        if (PENDING_PAYMENT.equals(order.getStatus()) && orderMapper.markCancelledIfPending(orderNo) == 1) {
            order.setStatus("CANCELLED");
            order.setCancelledAt(LocalDateTime.now());
            order.setCancelReason("USER_CANCELLED");
            order.setUpdatedAt(LocalDateTime.now());
            orderRecords.cancelled(order, false);
            coupons.release(orderNo);
            orderOutboxService.appendOrderEvent("ORDER_CANCELLED", order);
        } else if (PENDING_PAYMENT.equals(order.getStatus())) {
            throw new IllegalStateException("order changed during cancellation: " + orderNo);
        }
        return loadOrderResponse(order);
    }

    @Transactional
    public OrderApiModels.OrderResponse pay(Long userId, String orderNo) {
        OrderEntity order = requireOrderForUpdate(userId, orderNo);
        if (!PENDING_PAYMENT.equals(order.getStatus())) {
            throw new IllegalArgumentException("order cannot be paid in status: " + order.getStatus());
        }
        if (!order.getCreatedAt().plusMinutes(30).isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("order payment window expired: " + orderNo);
        }
        if (orderMapper.markPaidIfPending(orderNo) != 1) {
            throw new IllegalArgumentException("order was changed before payment: " + orderNo);
        }
        order.setStatus(PAID);
        order.setPaidAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderRecords.paid(order);
        coupons.consume(orderNo);
        orderOutboxService.appendOrderEvent("ORDER_PAID", order);
        return loadOrderResponse(order);
    }

    @Transactional
    public OrderApiModels.OrderResponse ship(Long orderNoUserId, String orderNo) {
        OrderEntity order = requireOrderForUpdate(orderNoUserId, orderNo);
        if (!PAID.equals(order.getStatus())) {
            throw new IllegalArgumentException("order cannot be shipped in status: " + order.getStatus());
        }
        if (orderMapper.markShippedIfPaid(orderNo) != 1) {
            throw new IllegalStateException("order changed during shipment: " + orderNo);
        }
        order.setStatus(SHIPPED);
        order.setShippedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderRecords.shipped(order);
        orderOutboxService.appendOrderEvent("ORDER_SHIPPED", order);
        return loadOrderResponse(order);
    }

    @Transactional
    public OrderApiModels.OrderResponse complete(Long userId, String orderNo) {
        OrderEntity order = requireOrderForUpdate(userId, orderNo);
        if (!SHIPPED.equals(order.getStatus())) {
            throw new IllegalArgumentException("order cannot be completed in status: " + order.getStatus());
        }
        if (orderMapper.markCompletedIfShipped(orderNo) != 1) {
            throw new IllegalStateException("order changed during completion: " + orderNo);
        }
        order.setStatus(COMPLETED);
        order.setCompletedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderRecords.completed(order);
        List<OrderItemEntity> items = orderItemMapper.selectList(Wrappers.<OrderItemEntity>lambdaQuery()
                .eq(OrderItemEntity::getOrderNo, orderNo));
        orderOutboxService.appendOrderEvent("ORDER_COMPLETED", order, items);
        return toResponse(order, items);
    }

    @Transactional
    public void cancelExpired(String orderNo) {
        OrderEntity order = orderMapper.selectOne(Wrappers.<OrderEntity>lambdaQuery()
                .eq(OrderEntity::getOrderNo, orderNo));
        if (order == null || !PENDING_PAYMENT.equals(order.getStatus())) {
            return;
        }
        if (orderMapper.markExpiredIfPending(orderNo, LocalDateTime.now().minusMinutes(30)) == 1) {
            order.setStatus("CANCELLED");
            order.setCancelledAt(LocalDateTime.now());
            order.setCancelReason("PAYMENT_TIMEOUT");
            order.setUpdatedAt(LocalDateTime.now());
            orderRecords.cancelled(order, true);
            coupons.release(orderNo);
            orderOutboxService.appendOrderEvent("ORDER_CANCELLED", order);
        }
    }

    private Map<Long, CheckoutLine> resolveItems(List<OrderApiModels.OrderItemRequest> items) {
        if (items == null || items.isEmpty() || items.size() > 100) {
            throw new IllegalArgumentException("order must contain 1 to 100 items");
        }
        Map<Long, ProductClient.SkuSnapshot> defaults = new LinkedHashMap<>();
        Map<Long, CheckoutLine> lines = new LinkedHashMap<>();
        for (OrderApiModels.OrderItemRequest item : items) {
            if (item == null || !item.isReferenceValid() || item.quantity() == null || item.quantity() <= 0
                    || (item.productId() != null && item.productId() <= 0)
                    || (item.skuId() != null && item.skuId() <= 0)) {
                throw new IllegalArgumentException("positive quantity and exactly one productId or skuId required");
            }
            ProductClient.SkuSnapshot sku;
            if (item.productId() != null) {
                sku = defaults.computeIfAbsent(item.productId(), id -> loadSku(id, true));
            } else if (lines.containsKey(item.skuId())) {
                sku = lines.get(item.skuId()).sku();
            } else {
                sku = loadSku(item.skuId(), false);
            }
            CheckoutLine existing = lines.get(sku.skuId());
            try {
                lines.put(sku.skuId(), existing == null ? new CheckoutLine(sku, item.quantity())
                        : new CheckoutLine(existing.sku(), Math.addExact(existing.quantity(), item.quantity())));
            } catch (ArithmeticException ex) {
                throw new IllegalArgumentException("order quantity exceeds supported range", ex);
            }
        }
        BigDecimal total = lines.values().stream()
                .map(line -> line.sku().price().multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new IllegalArgumentException("order amount exceeds supported range");
        }
        return lines;
    }

    private record CheckoutLine(ProductClient.SkuSnapshot sku, int quantity) { }

    private UserClient.AddressSnapshot loadAddress(Long userId, Long addressId) {
        if (addressId == null) return null;
        if (addressId <= 0) throw new IllegalArgumentException("positive addressId required");
        UserClient.AddressSnapshot address = userClient.address(addressId, userId);
        if (address == null || !addressId.equals(address.id()) || !userId.equals(address.userId())) {
            throw new IllegalArgumentException("address not found");
        }
        return address;
    }

    private String specJson(ProductClient.SkuSnapshot sku) {
        try {
            return json.writeValueAsString(sku.specs() == null ? Map.of() : sku.specs());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("cannot serialize SKU snapshot", ex);
        }
    }

    private OrderEntity requireOrderForUpdate(Long userId, String orderNo) {
        OrderEntity order = orderMapper.findOwnedForUpdate(userId, orderNo);
        if (order == null) {
            throw new IllegalArgumentException("order not found: " + orderNo);
        }
        return order;
    }

    private OrderApiModels.OrderResponse loadOrderResponse(OrderEntity order) {
        List<OrderItemEntity> items = orderItemMapper.selectList(Wrappers.<OrderItemEntity>lambdaQuery()
                .eq(OrderItemEntity::getOrderNo, order.getOrderNo()));
        return toResponse(order, items);
    }

    private ProductClient.SkuSnapshot loadSku(Long id, boolean defaultForProduct) {
        try {
            ProductClient.SkuSnapshot sku = defaultForProduct ? productClient.defaultSku(id) : productClient.sku(id);
            if (sku == null || sku.skuId() == null || sku.skuId() <= 0
                    || sku.productId() == null || sku.productId() <= 0 || sku.price() == null
                    || sku.price().signum() < 0 || sku.price().stripTrailingZeros().scale() > 2
                    || !Integer.valueOf(1).equals(sku.status())
                    || !(defaultForProduct ? id.equals(sku.productId()) : id.equals(sku.skuId()))) {
                throw new IllegalArgumentException("SKU is not available: " + id);
            }
            return sku;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("product service unavailable: " + id, ex);
        }
    }

    private void releaseReservations(String orderNo, List<Long> reservedProductIds) {
        if (reservedProductIds.isEmpty()) {
            return;
        }
        try {
            inventoryClient.release(new InventoryClient.ReleaseRequest(orderNo));
        } catch (RuntimeException releaseException) {
            log.error("failed to compensate inventory reservation, orderNo={}, products={}",
                    orderNo, reservedProductIds, releaseException);
        }
    }

    private OrderApiModels.OrderResponse toResponse(OrderEntity order, List<OrderItemEntity> items) {
        List<OrderApiModels.OrderItemResponse> itemResponses = items.stream().map(this::toItemResponse)
                .toList();
        return new OrderApiModels.OrderResponse(order.getId(), order.getOrderNo(), order.getUserId(),
                order.getTotalAmount(), order.getStatus(), itemResponses, order.getCreatedAt());
    }

    private OrderApiModels.OrderItemResponse toItemResponse(OrderItemEntity item) {
        return new OrderApiModels.OrderItemResponse(item.getProductId(), item.getSkuId(), item.getProductName(),
                item.getCoverImageUrl(), item.getSkuName(), item.getUnitPrice(), item.getQuantity(), item.getAmount());
    }
}
