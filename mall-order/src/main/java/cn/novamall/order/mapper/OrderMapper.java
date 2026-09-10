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

    @Update("UPDATE mall_order SET status = 'CANCELLED', cancelled_at = NOW(), updated_at = NOW() "
            + "WHERE order_no = #{orderNo} AND status = 'PENDING_PAYMENT'")
    int markCancelledIfPending(@Param("orderNo") String orderNo);

    @Update("UPDATE mall_order SET status = 'PAID', paid_at = NOW(), updated_at = NOW() "
            + "WHERE order_no = #{orderNo} AND status = 'PENDING_PAYMENT'")
    int markPaidIfPending(@Param("orderNo") String orderNo);

    @Update("UPDATE mall_order SET status = 'SHIPPED', shipped_at = NOW(), updated_at = NOW() "
            + "WHERE order_no = #{orderNo} AND status = 'PAID'")
    int markShippedIfPaid(@Param("orderNo") String orderNo);

    @Update("UPDATE mall_order SET status = 'COMPLETED', completed_at = NOW(), updated_at = NOW() "
            + "WHERE order_no = #{orderNo} AND status = 'SHIPPED'")
    int markCompletedIfShipped(@Param("orderNo") String orderNo);
}
