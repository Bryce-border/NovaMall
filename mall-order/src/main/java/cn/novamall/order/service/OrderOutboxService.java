package cn.novamall.order.service;

import cn.novamall.common.id.IdGenerator;
import cn.novamall.order.entity.OrderEntity;
import cn.novamall.order.entity.OrderOutboxEntity;
import cn.novamall.order.entity.OrderItemEntity;
import cn.novamall.order.mapper.OrderOutboxMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

@Service
public class OrderOutboxService {

    private static final String AGGREGATE_TYPE = "ORDER";
    private static final String NEW = "NEW";

    private final OrderOutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;
    private final org.springframework.jdbc.core.JdbcTemplate db;

    public OrderOutboxService(OrderOutboxMapper outboxMapper, ObjectMapper objectMapper, org.springframework.jdbc.core.JdbcTemplate db) {
        this.outboxMapper = outboxMapper;
        this.objectMapper = objectMapper;
        this.db = db;
    }

    public void appendOrderEvent(String eventType, OrderEntity order) {
        appendOrderEvent(eventType, order, List.of());
    }

    public void appendOrderEvent(String eventType, OrderEntity order, List<OrderItemEntity> items) {
        LocalDateTime now = LocalDateTime.now();
        List<OrderItemPayload> itemPayloads = items.stream()
                .map(item -> new OrderItemPayload(item.getProductId(), item.getQuantity()))
                .toList();
        OrderEventPayload payload = new OrderEventPayload(
                UUID.randomUUID().toString(), eventType, order.getOrderNo(), order.getUserId(),
                order.getTotalAmount(), now, itemPayloads);
        OrderOutboxEntity outbox = new OrderOutboxEntity();
        outbox.setId(IdGenerator.nextId());
        outbox.setMessageId(payload.eventId());
        outbox.setAggregateType(AGGREGATE_TYPE);
        outbox.setAggregateId(order.getOrderNo());
        outbox.setEventType(eventType);
        outbox.setPayload(toJson(payload));
        outbox.setStatus(NEW);
        outbox.setAttempts(0);
        outbox.setNextAttemptAt(now);
        outbox.setCreatedAt(now);
        outboxMapper.insert(outbox);
    }

    public void appendRefundEvent(String refundNo, String orderNo, List<RefundItemPayload> items) {
        LocalDateTime now = LocalDateTime.now();
        String eventId = UUID.randomUUID().toString();
        OrderOutboxEntity outbox = new OrderOutboxEntity();
        outbox.setId(IdGenerator.nextId());
        outbox.setMessageId(eventId);
        outbox.setAggregateType("REFUND");
        outbox.setAggregateId(refundNo);
        outbox.setEventType("REFUND_SUCCEEDED");
        outbox.setPayload(toJson(new RefundEventPayload(eventId, "REFUND_SUCCEEDED", refundNo, orderNo, now, items)));
        outbox.setStatus(NEW);
        outbox.setAttempts(0);
        outbox.setNextAttemptAt(now);
        outbox.setCreatedAt(now);
        outboxMapper.insert(outbox);
    }

    public record RefundEventPayload(String eventId, String eventType, String refundNo, String orderNo,
                                      LocalDateTime occurredAt, List<RefundItemPayload> items) { }
    public record RefundItemPayload(Long productId, Long skuId, Integer quantity) { }

    // The caller holds the original order lock. Absolute, versioned contributions
    // make completion/refund event delivery order irrelevant.
    public void appendSalesSnapshot(String orderNo) {
        if (!"COMPLETED".equals(db.queryForObject("SELECT status FROM mall_order WHERE order_no=?", String.class, orderNo))) return;
        db.update("INSERT INTO mall_order_sales_version(order_no,version) VALUES(?,1) ON DUPLICATE KEY UPDATE version=version+1",orderNo);
        long version=db.queryForObject("SELECT version FROM mall_order_sales_version WHERE order_no=?",Long.class,orderNo);
        var items=db.queryForList("""
            SELECT i.product_id,SUM(i.quantity-COALESCE(r.quantity,0)) AS quantity
            FROM mall_order_item i LEFT JOIN (
              SELECT ri.order_item_id,SUM(ri.quantity) AS quantity FROM mall_refund_item ri
              JOIN mall_refund r ON r.refund_no=ri.refund_no WHERE r.order_no=? AND r.status='SUCCESS' GROUP BY ri.order_item_id
            ) r ON r.order_item_id=i.id WHERE i.order_no=? GROUP BY i.product_id ORDER BY i.product_id
            """,orderNo,orderNo).stream().map(i->new SalesItemPayload(((Number)i.get("product_id")).longValue(),((Number)i.get("quantity")).longValue())).toList();
        String eventId=UUID.randomUUID().toString();
        LocalDateTime now=LocalDateTime.now();
        OrderOutboxEntity outbox=new OrderOutboxEntity();
        outbox.setId(IdGenerator.nextId());outbox.setMessageId(eventId);outbox.setAggregateType("ORDER");
        outbox.setAggregateId(orderNo);outbox.setEventType("ORDER_SALES_UPDATED");
        outbox.setPayload(toJson(new SalesEventPayload(eventId,"ORDER_SALES_UPDATED",orderNo,version,items)));
        outbox.setStatus(NEW);outbox.setAttempts(0);outbox.setNextAttemptAt(now);outbox.setCreatedAt(now);outboxMapper.insert(outbox);
    }
    public record SalesItemPayload(Long productId, Long quantity) { }
    public record SalesEventPayload(String eventId,String eventType,String orderNo,long version,List<SalesItemPayload> items) { }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize order event", ex);
        }
    }

    public record OrderEventPayload(String eventId, String eventType, String orderNo, Long userId,
                                    BigDecimal totalAmount, LocalDateTime occurredAt,
                                    List<OrderItemPayload> items) { }

    public record OrderItemPayload(Long productId, Integer quantity) { }
}
