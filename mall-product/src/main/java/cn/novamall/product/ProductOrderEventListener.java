package cn.novamall.product;

import cn.novamall.product.service.ProductRankingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ProductOrderEventListener {
    private final ObjectMapper objectMapper;
    private final ProductRankingService rankingService;

    public ProductOrderEventListener(ObjectMapper objectMapper, ProductRankingService rankingService) {
        this.objectMapper = objectMapper;
        this.rankingService = rankingService;
    }

    @RabbitListener(queues = cn.novamall.product.config.ProductMessagingConfig.SALES_QUEUE)
    public void onOrderCompleted(String payload) throws Exception {
        OrderCompletedEvent event = objectMapper.readValue(payload, OrderCompletedEvent.class);
        if (event == null || !"ORDER_COMPLETED".equals(event.eventType())
                || event.eventId() == null || event.items() == null) {
            throw new IllegalArgumentException("invalid order completed event");
        }
        // Drain legacy completion notifications. Migration 012 seeds their existing
        // contribution; new orders use ORDER_SALES_UPDATED with durable versions.
    }

    public record OrderCompletedEvent(String eventId, String eventType, String orderNo,
                                      Long userId, java.math.BigDecimal totalAmount,
                                      java.time.LocalDateTime occurredAt,
                                      java.util.List<OrderItem> items) { }

    public record OrderItem(Long productId, Integer quantity) { }
}
