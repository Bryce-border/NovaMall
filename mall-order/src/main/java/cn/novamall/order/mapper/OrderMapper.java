package cn.novamall.order.mapper;

import cn.novamall.order.entity.OrderEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderMapper extends BaseMapper<OrderEntity> {

    default Page<OrderEntity> findPageByUser(Long userId, String status, long page, long size) {
        var query = Wrappers.<OrderEntity>lambdaQuery().eq(OrderEntity::getUserId, userId)
                .eq(status != null && !status.isBlank(), OrderEntity::getStatus, status)
                .orderByDesc(OrderEntity::getCreatedAt).orderByDesc(OrderEntity::getId);
        return selectPage(new Page<>(page, size), query);
    }

    @Select("SELECT * FROM mall_order WHERE order_no = #{orderNo} AND user_id = #{userId} FOR UPDATE")
    OrderEntity findOwnedForUpdate(@Param("userId") Long userId, @Param("orderNo") String orderNo);

    @Select("SELECT * FROM mall_order WHERE status = 'PENDING_PAYMENT' "
            + "AND created_at < #{cutoff} ORDER BY created_at LIMIT #{limit}")
    List<OrderEntity> findPendingBefore(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

    @Update("UPDATE mall_order SET status = 'CANCELLED', cancel_reason = 'USER_CANCELLED', "
            + "cancelled_at = NOW(), updated_at = NOW() "
            + "WHERE order_no = #{orderNo} AND status = 'PENDING_PAYMENT'")
    int markCancelledIfPending(@Param("orderNo") String orderNo);

    @Update("UPDATE mall_order SET status = 'CANCELLED', cancel_reason = 'PAYMENT_TIMEOUT', "
            + "cancelled_at = NOW(), updated_at = NOW() "
            + "WHERE order_no = #{orderNo} AND status = 'PENDING_PAYMENT' AND created_at <= #{cutoff}")
    int markExpiredIfPending(@Param("orderNo") String orderNo, @Param("cutoff") LocalDateTime cutoff);

    @Update("UPDATE mall_order SET status = 'PAID', paid_at = NOW(), updated_at = NOW() "
            + "WHERE order_no = #{orderNo} AND status = 'PENDING_PAYMENT'")
    int markPaidIfPending(@Param("orderNo") String orderNo);

    @Update("UPDATE mall_order SET status = 'SHIPPED', shipped_at = NOW(), updated_at = NOW() "
            + "WHERE order_no = #{orderNo} AND status = 'PAID'")
    int markShippedIfPaid(@Param("orderNo") String orderNo);

    @Select("SELECT COUNT(*) FROM mall_refund WHERE order_no=#{orderNo} "
            + "AND status NOT IN ('SUCCESS','REJECTED','WITHDRAWN')")
    int pendingRefunds(@Param("orderNo") String orderNo);

    @Select("""
            SELECT COUNT(*) FROM mall_order_item i WHERE i.order_no=#{orderNo} AND i.quantity > COALESCE((
                SELECT SUM(ri.quantity) FROM mall_refund_item ri JOIN mall_refund r ON r.refund_no=ri.refund_no
                WHERE ri.order_item_id=i.id AND r.status='SUCCESS'),0)
            """)
    int remainingItems(@Param("orderNo") String orderNo);

    @Select("""
            SELECT i.*, i.quantity-COALESCE(u.refunded_quantity,0) AS fulfilled_quantity
            FROM mall_order_item i LEFT JOIN (
                SELECT ri.order_item_id,SUM(ri.quantity) AS refunded_quantity FROM mall_refund_item ri
                JOIN mall_refund r ON r.refund_no=ri.refund_no WHERE r.order_no=#{orderNo} AND r.status='SUCCESS'
                GROUP BY ri.order_item_id
            ) u ON u.order_item_id=i.id WHERE i.order_no=#{orderNo} ORDER BY i.id
            """)
    List<java.util.Map<String,Object>> fulfillmentItems(@Param("orderNo") String orderNo);

    @Select("""
            SELECT COUNT(*) AS total_count,
              COALESCE(SUM(CASE WHEN status='SUCCESS' THEN amount ELSE 0 END),0) AS refunded_amount,
              COALESCE(SUM(CASE WHEN status NOT IN ('SUCCESS','REJECTED','WITHDRAWN') THEN amount ELSE 0 END),0) AS pending_amount
            FROM mall_refund WHERE order_no=#{orderNo}
            """)
    java.util.Map<String,Object> refundSummary(@Param("orderNo") String orderNo);

    @Select("""
            <script>
            SELECT r.order_no,
              COALESCE(SUM(CASE WHEN r.status='SUCCESS' THEN ri.quantity ELSE 0 END),0) AS refunded_quantity,
              COALESCE(SUM(CASE WHEN r.status NOT IN ('SUCCESS','REJECTED','WITHDRAWN') THEN 1 ELSE 0 END),0) AS pending_count
            FROM mall_refund r JOIN mall_refund_item ri ON ri.refund_no=r.refund_no
            WHERE r.order_no IN
              <foreach collection="orderNos" item="no" open="(" separator="," close=")">#{no}</foreach>
            GROUP BY r.order_no
            </script>
            """)
    List<java.util.Map<String,Object>> refundSummaries(@Param("orderNos") List<String> orderNos);

    @Update("UPDATE mall_order SET status = 'COMPLETED', completed_at = NOW(), updated_at = NOW() "
            + "WHERE order_no = #{orderNo} AND status = 'SHIPPED'")
    int markCompletedIfShipped(@Param("orderNo") String orderNo);
}
