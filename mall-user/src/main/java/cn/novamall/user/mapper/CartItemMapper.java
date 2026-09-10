package cn.novamall.user.mapper;
import cn.novamall.user.entity.CartItemEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface CartItemMapper extends BaseMapper<CartItemEntity> {
    @Select("SELECT * FROM mall_cart_item WHERE user_id = #{userId} AND selected = 1 ORDER BY id LIMIT 101")
    List<CartItemEntity> selected(@Param("userId") Long userId);

    @Insert("INSERT INTO mall_cart_item (id, user_id, product_id, sku_id, quantity, selected, version, created_at, updated_at) "
            + "VALUES (#{id}, #{userId}, #{productId}, #{skuId}, #{quantity}, 1, 0, #{createdAt}, #{updatedAt}) "
            + "ON DUPLICATE KEY UPDATE quantity = quantity + #{quantity}, version = version + 1, updated_at = #{updatedAt}")
    int addQuantity(CartItemEntity item);

    @Update("UPDATE mall_cart_item SET quantity = #{quantity}, selected = #{selected}, version = version + 1, "
            + "updated_at = #{updatedAt} WHERE id = #{id} AND user_id = #{userId} AND version = #{version}")
    int updateVersioned(CartItemEntity item);

    @Delete("DELETE FROM mall_cart_item WHERE id = #{id} AND user_id = #{userId} AND version = #{version} AND selected = 1")
    int deletePurchased(@Param("userId") Long userId, @Param("id") Long id, @Param("version") Long version);
}
