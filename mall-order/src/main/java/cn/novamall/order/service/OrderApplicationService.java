package cn.novamall.order.service;

import cn.novamall.common.id.IdGenerator;
import cn.novamall.order.OrderApiModels;
import cn.novamall.order.client.InventoryClient;
import cn.novamall.order.client.ProductClient;
import cn.novamall.order.entity.OrderEntity;
import cn.novamall.order.entity.OrderItemEntity;
import cn.novamall.order.mapper.OrderItemMapper;
import cn.novamall.order.mapper.OrderMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public OrderApplicationService(OrderMapper orderMapper, OrderItemMapper orderItemMapper,
                                   ProductClient productClient, InventoryClient inventoryClient,
                                   OrderOutboxService orderOutboxService) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productClient = productClient;
        this.inventoryClient = inventoryClient;
        this.orderOutboxService = orderOutboxService;
    }

    @Transactional
    public OrderApiModels.OrderResponse create(Long userId, String userName,
                                               OrderApiModels.CreateOrderRequest request) {
        String orderNo = String.valueOf(IdGenerator.nextId());
        List<Long> reservedProductIds = new ArrayList<>();
        try {
            Map<Long, Integer> quantities = mergeItems(request.items());
            List<OrderItemEntity> orderItems = new ArrayList<>();
            BigDecimal totalAmount = BigDecimal.ZERO;
            for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
                ProductClient.ProductSnapshot product = loadProduct(entry.getKey());
                InventoryClient.ReserveResponse reservation = inventoryClient.reserve(
                        new InventoryClient.ReserveRequest(entry.getKey(), entry.getValue(), orderNo));
                if (reservation == null || !reservation.success()) {
                    throw new IllegalArgumentException("inventory reservation failed: "
                            + (reservation == null ? "empty response" : reservation.message()));
                }
                reservedProductIds.add(entry.getKey());

                BigDecimal amount = product.price().multiply(BigDecimal.valueOf(entry.getValue()));
                OrderItemEntity item = new OrderItemEntity();
                item.setProductId(product.id());
                item.setProductName(product.name());
                item.setUnitPrice(product.price());
                item.setQuantity(entry.getValue());
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
            order.setTotalAmount(totalAmount);
            order.setStatus(PENDING_PAYMENT);
            order.setCreatedAt(now);
            order.setUpdatedAt(now);
            orderMapper.insert(order);
            for (OrderItemEntity item : orderItems) {
                item.setId(IdGenerator.nextId());
                item.setOrderId(order.getId());
                item.setOrderNo(orderNo);
                orderItemMapper.insert(item);
            }
            orderOutboxService.appendOrderEvent("ORDER_CREATED", order);
            return toResponse(order, orderItems);
        } catch (RuntimeException ex) {
            releaseReservations(orderNo, reservedProductIds);
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

    @Transactional
    public OrderApiModels.OrderResponse cancel(Long userId, String orderNo) {
        OrderEntity order = requireOrderForUpdate(userId, orderNo);
        if ("CANCELLED".equals(order.getStatus())) return loadOrderResponse(order);
        if (!PENDING_PAYMENT.equals(order.getStatus())) {
            throw new IllegalArgumentException("order cannot be cancelled in status: " + order.getStatus());
        }
        if (PENDING_PAYMENT.equals(order.getStatus()) && orderMapper.markCancelledIfPending(orderNo) == 1) {
            order.setStatus("CANCELLED");
            order.setUpdatedAt(LocalDateTime.now());
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
        order.setUpdatedAt(LocalDateTime.now());
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
        order.setUpdatedAt(LocalDateTime.now());
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
        order.setUpdatedAt(LocalDateTime.now());
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
        if (orderMapper.markCancelledIfPending(orderNo) == 1) {
            order.setStatus("CANCELLED");
            order.setUpdatedAt(LocalDateTime.now());
            orderOutboxService.appendOrderEvent("ORDER_CANCELLED", order);
        }
    }

    private Map<Long, Integer> mergeItems(List<OrderApiModels.OrderItemRequest> items) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (OrderApiModels.OrderItemRequest item : items) {
            quantities.merge(item.productId(), item.quantity(), Math::addExact);
        }
        return quantities;
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

    private ProductClient.ProductSnapshot loadProduct(Long productId) {
        try {
            ProductClient.ProductSnapshot product = productClient.get(productId);
            if (product == null || product.id() == null || product.price() == null
                    || !Integer.valueOf(1).equals(product.status())) {
                throw new IllegalArgumentException("product is not available: " + productId);
            }
            return product;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("product service unavailable: " + productId, ex);
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
        List<OrderApiModels.OrderItemResponse> itemResponses = items.stream()
                .map(item -> new OrderApiModels.OrderItemResponse(item.getProductId(), item.getProductName(),
                        item.getUnitPrice(), item.getQuantity(), item.getAmount()))
                .toList();
        return new OrderApiModels.OrderResponse(order.getId(), order.getOrderNo(), order.getUserId(),
                order.getTotalAmount(), order.getStatus(), itemResponses, order.getCreatedAt());
    }
}
