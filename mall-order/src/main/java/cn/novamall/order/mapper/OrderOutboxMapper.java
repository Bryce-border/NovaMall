package cn.novamall.order.mapper;

import cn.novamall.order.entity.OrderOutboxEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderOutboxMapper extends BaseMapper<OrderOutboxEntity> {

    @Select("SELECT * FROM mall_order_outbox "
            + "WHERE (status = 'NEW' OR (status = 'FAILED' AND next_attempt_at <= #{now})) "
            + "ORDER BY created_at LIMIT #{limit}")
    List<OrderOutboxEntity> findPending(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("UPDATE mall_order_outbox SET status = 'PUBLISHED', published_at = NOW(), "
            + "last_error = NULL WHERE id = #{id} AND status IN ('NEW', 'FAILED')")
    int markPublished(@Param("id") Long id);

    @Update("UPDATE mall_order_outbox SET status = 'FAILED', attempts = attempts + 1, "
            + "next_attempt_at = #{nextAttemptAt}, last_error = #{lastError} "
            + "WHERE id = #{id} AND status IN ('NEW', 'FAILED')")
    int markFailed(@Param("id") Long id, @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
                   @Param("lastError") String lastError);
}
