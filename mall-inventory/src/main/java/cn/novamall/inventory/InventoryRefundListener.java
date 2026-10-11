package cn.novamall.inventory;

import cn.novamall.inventory.service.InventoryRefundService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class InventoryRefundListener {
    private final ObjectMapper json;
    private final InventoryRefundService refunds;
    public InventoryRefundListener(ObjectMapper json, InventoryRefundService refunds) { this.json = json; this.refunds = refunds; }

    @RabbitListener(queues = "mall.inventory.refund-succeeded")
    public void onRefund(String payload) throws Exception {
        Event event = json.readValue(payload, Event.class);
        if (event == null || !"REFUND_SUCCEEDED".equals(event.eventType())) throw new IllegalArgumentException("invalid refund event");
        refunds.restock(event.refundNo(), event.orderNo(), event.items());
    }
    public record Event(String eventId, String eventType, String refundNo, String orderNo,
                        LocalDateTime occurredAt, List<InventoryRefundService.Line> items) { }
}
