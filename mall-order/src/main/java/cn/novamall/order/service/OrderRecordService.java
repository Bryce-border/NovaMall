package cn.novamall.order.service;

import cn.novamall.common.id.IdGenerator;
import cn.novamall.order.entity.OrderEntity;
import cn.novamall.order.mapper.OrderRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class OrderRecordService {
    private final OrderRecordMapper records;

    public OrderRecordService(OrderRecordMapper records) {
        this.records = records;
    }

    public void created(OrderEntity order) {
        history(order, null, "ORDER_CREATED", "USER", order.getUserId(), "Order created");
    }

    public void paid(OrderEntity order) {
        requireInserted(records.insertMockPayment(IdGenerator.nextId(), order.getId()));
        history(order, "PENDING_PAYMENT", "ORDER_PAID", "USER", order.getUserId(),
                "Simulated payment; no money charged");
    }

    public void shipped(OrderEntity order) {
        requireInserted(records.insertMockShipment(IdGenerator.nextId(), order.getId()));
        history(order, "PAID", "ORDER_SHIPPED", "USER", order.getUserId(),
                "Simulated shipment; no parcel dispatched");
    }

    public void completed(OrderEntity order) {
        // Older shipped orders may have no shipment row; do not invent one on receipt.
        records.completeMockShipment(order.getId());
        history(order, "SHIPPED", "ORDER_COMPLETED", "USER", order.getUserId(),
                "Simulated receipt confirmed by buyer");
    }

    public void cancelled(OrderEntity order, boolean expired) {
        history(order, "PENDING_PAYMENT", "ORDER_CANCELLED", expired ? "SYSTEM" : "USER",
                expired ? null : order.getUserId(), expired ? "Payment window expired" : "Cancelled by buyer");
    }

    private void history(OrderEntity order, String fromStatus, String eventType,
                         String actorType, Long actorId, String note) {
        requireInserted(records.insertHistory(IdGenerator.nextId(), order.getId(), fromStatus,
                eventType, actorType, actorId, note));
    }

    private void requireInserted(int affectedRows) {
        if (affectedRows != 1) throw new IllegalStateException("order record was not written");
    }
}
