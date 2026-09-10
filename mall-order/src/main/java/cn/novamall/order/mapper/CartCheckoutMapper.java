package cn.novamall.order.mapper;

import org.apache.ibatis.annotations.*;

@Mapper
public interface CartCheckoutMapper {
    @Insert("INSERT INTO mall_cart_checkout (user_id, request_key, address_id, created_at) "
            + "VALUES (#{userId}, #{requestKey}, #{addressId}, NOW()) "
            + "ON DUPLICATE KEY UPDATE request_key = request_key")
    int claim(@Param("userId") Long userId, @Param("requestKey") String requestKey,
              @Param("addressId") Long addressId);

    @Select("SELECT user_id, request_key, address_id, order_no FROM mall_cart_checkout "
            + "WHERE user_id = #{userId} AND request_key = #{requestKey} FOR UPDATE")
    Checkout findForUpdate(@Param("userId") Long userId, @Param("requestKey") String requestKey);

    @Update("UPDATE mall_cart_checkout SET order_no = #{orderNo} "
            + "WHERE user_id = #{userId} AND request_key = #{requestKey} AND order_no IS NULL")
    int complete(@Param("userId") Long userId, @Param("requestKey") String requestKey,
                 @Param("orderNo") String orderNo);

    record Checkout(Long userId, String requestKey, Long addressId, String orderNo) { }
}
