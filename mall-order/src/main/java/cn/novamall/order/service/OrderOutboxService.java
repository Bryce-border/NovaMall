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

    public OrderOutboxService(OrderOutboxMapper outboxMapper, ObjectMapper objectMapper) {
        this.outboxMapper = outboxMapper;
        this.objectMapper = objectMapper;
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

    private String toJson(OrderEventPayload payload) {
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
