package cn.novamall.order;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.List;

public final class OrderDetailApiModels {
    private OrderDetailApiModels() { }

    public record Detail(String id, String orderNo, String status, String currency,
                         String productAmount, String discountAmount, String freightAmount,
                         String totalAmount, String remark, String cancelReason,
                         Address address, List<Item> items, List<Payment> payments,
                         List<Shipment> shipments, List<History> history, List<Action> actions,
                         LocalDateTime paymentDeadline, LocalDateTime createdAt, LocalDateTime updatedAt,
                         LocalDateTime paidAt, LocalDateTime shippedAt, LocalDateTime completedAt,
                         LocalDateTime cancelledAt) { }

    public record Address(String receiverName, String receiverPhone, String province, String city,
                          String district, String detailAddress, String postalCode) { }

    public record Item(String productId, String skuId, String productName, String coverImageUrl,
                       String skuName, JsonNode specs, String unitPrice, int quantity, String amount) { }

    public record Payment(String paymentNo, String channel, boolean simulated, String amount,
                          String status, LocalDateTime paidAt, LocalDateTime createdAt) { }

    public record Shipment(String company, String trackingNo, boolean simulated, String status,
                           LocalDateTime shippedAt, LocalDateTime deliveredAt, LocalDateTime createdAt) { }

    public record History(String fromStatus, String toStatus, String eventType, String actorType,
                          String actorId, String note, LocalDateTime occurredAt) { }

    public record Action(String code, String label, boolean simulated) { }
}
