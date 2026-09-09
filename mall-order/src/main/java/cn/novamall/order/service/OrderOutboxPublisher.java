package cn.novamall.order.service;

import cn.novamall.order.config.OrderMessagingConfig;
import cn.novamall.order.entity.OrderOutboxEntity;
import cn.novamall.order.mapper.OrderOutboxMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class OrderOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderOutboxPublisher.class);
    private static final String ORDER_CREATED = "ORDER_CREATED";
    private static final String ORDER_CANCELLED = "ORDER_CANCELLED";
    private static final String ORDER_PAID = "ORDER_PAID";
    private static final String ORDER_SHIPPED = "ORDER_SHIPPED";
    private static final String ORDER_COMPLETED = "ORDER_COMPLETED";

    private final OrderOutboxMapper outboxMapper;
    private final RabbitTemplate rabbitTemplate;

    public OrderOutboxPublisher(OrderOutboxMapper outboxMapper, RabbitTemplate rabbitTemplate) {
        this.outboxMapper = outboxMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelayString = "${novamall.outbox.publish-delay-ms:5000}")
    public void publishPending() {
        List<OrderOutboxEntity> pending = outboxMapper.findPending(LocalDateTime.now(), 50);
        for (OrderOutboxEntity message : pending) {
            publishOne(message);
        }
    }

    private void publishOne(OrderOutboxEntity message) {
        try {
            CorrelationData correlation = new CorrelationData(message.getMessageId());
            rabbitTemplate.convertAndSend(OrderMessagingConfig.EVENTS_EXCHANGE,
                    routingKey(message.getEventType()), message.getPayload(), correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (confirm == null || !confirm.isAck()) {
                throw new IllegalStateException(confirm == null ? "publisher confirm missing" : confirm.getReason());
            }
            if (correlation.getReturned() != null) {
                throw new IllegalStateException("message was not routed: "
                        + correlation.getReturned().getReplyText());
            }
            outboxMapper.markPublished(message.getId());
        } catch (Exception ex) {
            LocalDateTime retryAt = LocalDateTime.now().plusSeconds(retryDelaySeconds(message.getAttempts()));
            String error = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            outboxMapper.markFailed(message.getId(), retryAt, truncate(error));
            log.warn("order event publish failed, messageId={}, retryAt={}",
                    message.getMessageId(), retryAt, ex);
        }
    }

    private String routingKey(String eventType) {
        return switch (eventType) {
            case ORDER_CREATED -> "order.created";
            case ORDER_CANCELLED -> "order.cancelled";
            case ORDER_PAID -> "order.paid";
            case ORDER_SHIPPED -> "order.shipped";
            case ORDER_COMPLETED -> "order.completed";
            default -> throw new IllegalArgumentException("unsupported order event: " + eventType);
        };
    }

    private long retryDelaySeconds(Integer attempts) {
        int currentAttempts = attempts == null ? 0 : attempts;
        return Math.min(300L, 1L << Math.min(currentAttempts + 1, 8));
    }

    private String truncate(String error) {
        return error.length() <= 500 ? error : error.substring(0, 500);
    }
}
