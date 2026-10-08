package cn.novamall.order.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderRecordMapper {
    // Select timestamps and amounts from the updated order in the same transaction.
    @Insert("""
            INSERT INTO mall_payment
                (id, order_id, order_no, payment_no, channel, amount, status, paid_at, created_at, updated_at)
            SELECT #{id}, id, order_no, CONCAT('MOCK-PAY-', order_no), 'MOCK', total_amount,
                   'SUCCESS', paid_at, paid_at, paid_at
            FROM mall_order WHERE id = #{orderId} AND status = 'PAID'
            """)
    int insertMockPayment(@Param("id") Long id, @Param("orderId") Long orderId);

    @Insert("""
            INSERT INTO mall_shipment
                (id, order_id, order_no, company, tracking_no, status, shipped_at, created_at, updated_at)
            SELECT #{id}, id, order_no, 'MOCK', CONCAT('MOCK-SHIP-', order_no), 'SHIPPED',
                   shipped_at, shipped_at, shipped_at
            FROM mall_order WHERE id = #{orderId} AND status = 'SHIPPED'
            """)
    int insertMockShipment(@Param("id") Long id, @Param("orderId") Long orderId);

    @Update("""
            UPDATE mall_shipment s JOIN mall_order o ON o.id = s.order_id
            SET s.status = 'DELIVERED', s.delivered_at = o.completed_at, s.updated_at = o.completed_at
            WHERE o.id = #{orderId} AND o.status = 'COMPLETED'
              AND s.company = 'MOCK' AND s.status = 'SHIPPED'
            """)
    int completeMockShipment(@Param("orderId") Long orderId);

    @Insert("""
            INSERT INTO mall_order_history
                (id, order_id, order_no, from_status, to_status, event_type,
                 actor_type, actor_id, note, occurred_at)
            SELECT #{id}, id, order_no, #{fromStatus}, status, #{eventType},
                   #{actorType}, #{actorId}, #{note}, updated_at
            FROM mall_order WHERE id = #{orderId}
            """)
    int insertHistory(@Param("id") Long id, @Param("orderId") Long orderId,
                      @Param("fromStatus") String fromStatus, @Param("eventType") String eventType,
                      @Param("actorType") String actorType, @Param("actorId") Long actorId,
                      @Param("note") String note);

    @Select("""
            SELECT payment_no, channel, amount, status, paid_at, created_at
            FROM mall_payment WHERE order_id = #{orderId} ORDER BY created_at, id
            """)
    List<PaymentRecord> payments(@Param("orderId") Long orderId);

    @Select("""
            SELECT company, tracking_no, status, shipped_at, delivered_at, created_at
            FROM mall_shipment WHERE order_id = #{orderId} ORDER BY created_at, id
            """)
    List<ShipmentRecord> shipments(@Param("orderId") Long orderId);

    @Select("""
            SELECT from_status, to_status, event_type, actor_type, actor_id, note, occurred_at
            FROM mall_order_history WHERE order_id = #{orderId} ORDER BY occurred_at, id
            """)
    List<HistoryRecord> history(@Param("orderId") Long orderId);

    record PaymentRecord(String paymentNo, String channel, BigDecimal amount, String status,
                         LocalDateTime paidAt, LocalDateTime createdAt) { }
    record ShipmentRecord(String company, String trackingNo, String status, LocalDateTime shippedAt,
                          LocalDateTime deliveredAt, LocalDateTime createdAt) { }
    record HistoryRecord(String fromStatus, String toStatus, String eventType, String actorType,
                         Long actorId, String note, LocalDateTime occurredAt) { }
}
