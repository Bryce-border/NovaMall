package cn.novamall.order.service;

import cn.novamall.order.OrderDetailApiModels;
import cn.novamall.order.OrderDetailApiModels.*;
import cn.novamall.order.entity.OrderAddressEntity;
import cn.novamall.order.entity.OrderEntity;
import cn.novamall.order.entity.OrderItemEntity;
import cn.novamall.order.mapper.OrderAddressMapper;
import cn.novamall.order.mapper.OrderItemMapper;
import cn.novamall.order.mapper.OrderMapper;
import cn.novamall.order.mapper.OrderRecordMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderDetailService {
    private final OrderMapper orders;
    private final OrderItemMapper items;
    private final OrderAddressMapper addresses;
    private final OrderRecordMapper records;
    private final ObjectMapper json;
    private final boolean mockPaymentEnabled;
    private final boolean mockFulfillmentEnabled;

    public OrderDetailService(OrderMapper orders, OrderItemMapper items, OrderAddressMapper addresses,
                              OrderRecordMapper records, ObjectMapper json,
                              @Value("${novamall.payment.mock-enabled:false}") boolean mockPaymentEnabled,
                              @Value("${novamall.fulfillment.mock-enabled:false}") boolean mockFulfillmentEnabled) {
        this.orders = orders;
        this.items = items;
        this.addresses = addresses;
        this.records = records;
        this.json = json;
        this.mockPaymentEnabled = mockPaymentEnabled;
        this.mockFulfillmentEnabled = mockFulfillmentEnabled;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OrderDetailApiModels.Detail get(Long userId, String orderNo) {
        // Check ownership before querying address, payment or shipment information.
        OrderEntity order = orders.selectOne(Wrappers.<OrderEntity>lambdaQuery()
                .eq(OrderEntity::getUserId, userId).eq(OrderEntity::getOrderNo, orderNo));
        if (order == null) throw new IllegalArgumentException("order not found: " + orderNo);

        var quantities = new java.util.HashMap<Long, Integer>();
        orders.fulfillmentItems(orderNo).forEach(i -> quantities.put(((Number)i.get("id")).longValue(),
                ((Number)i.get("fulfilled_quantity")).intValue()));
        var refundAmounts = orders.refundSummary(orderNo);
        int pendingRefunds = orders.pendingRefunds(orderNo);
        long refundCount = ((Number)refundAmounts.get("total_count")).longValue();
        boolean fullyRefunded = refundCount > 0 && orders.remainingItems(orderNo) == 0;
        var refundSummary = new RefundSummary(money((BigDecimal)refundAmounts.get("refunded_amount")),
                money((BigDecimal)refundAmounts.get("pending_amount")), pendingRefunds, refundCount, fullyRefunded);

        OrderAddressEntity address = addresses.selectOne(Wrappers.<OrderAddressEntity>lambdaQuery()
                .eq(OrderAddressEntity::getOrderId, order.getId()));
        List<Item> itemDetails = items.selectList(Wrappers.<OrderItemEntity>lambdaQuery()
                .eq(OrderItemEntity::getOrderId, order.getId()).orderByAsc(OrderItemEntity::getId))
                .stream().map(item -> new Item(id(item.getProductId()), id(item.getSkuId()), item.getProductName(),
                        item.getCoverImageUrl(), item.getSkuName(), specs(item.getSpecJson()),
                        money(item.getUnitPrice()), item.getQuantity(), money(item.getAmount()),
                        item.getQuantity() - quantities.getOrDefault(item.getId(), item.getQuantity()),
                        quantities.getOrDefault(item.getId(), item.getQuantity()))).toList();
        List<Payment> payments = records.payments(order.getId()).stream()
                .map(p -> new Payment(p.paymentNo(), p.channel(), "MOCK".equals(p.channel()), money(p.amount()),
                        p.status(), p.paidAt(), p.createdAt())).toList();
        List<Shipment> shipments = records.shipments(order.getId()).stream()
                .map(s -> new Shipment(s.company(), s.trackingNo(), "MOCK".equals(s.company()), s.status(),
                        s.shippedAt(), s.deliveredAt(), s.createdAt())).toList();
        List<History> history = records.history(order.getId()).stream()
                .map(h -> new History(h.fromStatus(), h.toStatus(), h.eventType(), h.actorType(),
                        id(h.actorId()), h.note(), h.occurredAt())).toList();

        return new Detail(id(order.getId()), order.getOrderNo(), order.getStatus(), "CNY",
                money(order.getProductAmount()), money(order.getDiscountAmount()), money(order.getFreightAmount()),
                money(order.getTotalAmount()), order.getRemark(), order.getCancelReason(),
                address == null ? null : new Address(address.getReceiverName(), address.getReceiverPhone(),
                        address.getProvince(), address.getCity(), address.getDistrict(),
                        address.getDetailAddress(), address.getPostalCode()),
                itemDetails, payments, shipments, history, actions(order, pendingRefunds, fullyRefunded), order.getCreatedAt().plusMinutes(30),
                order.getCreatedAt(), order.getUpdatedAt(), order.getPaidAt(), order.getShippedAt(),
                order.getCompletedAt(), order.getCancelledAt(), refundSummary);
    }

    private List<Action> actions(OrderEntity order, int pendingRefunds, boolean fullyRefunded) {
        List<Action> available = new ArrayList<>();
        switch (order.getStatus()) {
            case "PENDING_PAYMENT" -> {
                available.add(new Action("CANCEL", "取消订单", false));
                if (mockPaymentEnabled && order.getCreatedAt().plusMinutes(30).isAfter(LocalDateTime.now())) {
                    available.add(new Action("MOCK_PAY", "模拟支付", true));
                }
            }
            case "PAID" -> {
                if (mockFulfillmentEnabled && pendingRefunds == 0 && !fullyRefunded)
                    available.add(new Action("MOCK_SHIP", "模拟发货", true));
            }
            case "SHIPPED" -> {
                if (mockFulfillmentEnabled && pendingRefunds == 0 && !fullyRefunded)
                    available.add(new Action("MOCK_COMPLETE", "模拟确认收货", true));
            }
            default -> { }
        }
        return List.copyOf(available);
    }

    private JsonNode specs(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) return json.createObjectNode();
        try {
            return json.readTree(snapshot);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("invalid order SKU snapshot", ex);
        }
    }

    private String id(Long value) { return value == null ? null : value.toString(); }
    private String money(BigDecimal value) { return value == null ? null : value.setScale(2).toPlainString(); }
}
