package cn.novamall.inventory;

import cn.novamall.inventory.service.InventoryApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryOrderEventListener {

    private static final String CANCEL_QUEUE = "mall.inventory.order-cancelled";

    private final ObjectMapper objectMapper;
    private final InventoryApplicationService inventoryService;

    public InventoryOrderEventListener(ObjectMapper objectMapper,
                                       InventoryApplicationService inventoryService) {
        this.objectMapper = objectMapper;
        this.inventoryService = inventoryService;
    }

    @RabbitListener(queues = CANCEL_QUEUE)
    public void onOrderCancelled(String payload) throws Exception {
        OrderCancelledEvent event = objectMapper.readValue(payload, OrderCancelledEvent.class);
        inventoryService.release(event.orderNo());
    }

    public record OrderCancelledEvent(String eventId, String eventType, String orderNo,
                                      Long userId, java.math.BigDecimal totalAmount,
                                      java.time.LocalDateTime occurredAt) { }

    @RabbitListener(queues = cn.novamall.inventory.config.InventoryMessagingConfig.PAID_QUEUE)
    public void onOrderPaid(String payload) throws Exception {
        OrderPaidEvent event = objectMapper.readValue(payload, OrderPaidEvent.class);
        if (event == null || !"ORDER_PAID".equals(event.eventType())) {
            throw new IllegalArgumentException("invalid order paid event");
        }
        inventoryService.confirm(event.orderNo());
    }

    public record OrderPaidEvent(String eventId, String eventType, String orderNo,
                                 Long userId, java.math.BigDecimal totalAmount,
                                 java.time.LocalDateTime occurredAt) { }
}
