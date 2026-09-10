package cn.novamall.order.mapper;

import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface CartCleanupMapper {
    @Insert("INSERT INTO mall_cart_cleanup (id, user_id, order_no, payload, attempts, next_attempt_at, created_at) "
            + "VALUES (#{id}, #{userId}, #{orderNo}, #{payload}, 0, NOW(), NOW())")
    int append(@Param("id") Long id, @Param("userId") Long userId,
               @Param("orderNo") String orderNo, @Param("payload") String payload);

    @Select("SELECT id, user_id, payload, attempts FROM mall_cart_cleanup "
            + "WHERE next_attempt_at <= NOW() ORDER BY next_attempt_at, id LIMIT 50")
    List<Task> pending();

    @Delete("DELETE FROM mall_cart_cleanup WHERE id = #{id}")
    int done(@Param("id") Long id);

    @Update("UPDATE mall_cart_cleanup SET attempts = attempts + 1, "
            + "next_attempt_at = TIMESTAMPADD(SECOND, #{delay}, NOW()) WHERE id = #{id}")
    int retry(@Param("id") Long id, @Param("delay") long delay);

    record Task(Long id, Long userId, String payload, Integer attempts) { }
}
