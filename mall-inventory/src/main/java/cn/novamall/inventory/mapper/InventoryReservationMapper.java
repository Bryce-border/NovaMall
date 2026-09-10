package cn.novamall.inventory.mapper;

import cn.novamall.inventory.entity.InventoryReservationEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface InventoryReservationMapper extends BaseMapper<InventoryReservationEntity> {

    @Select("SELECT * FROM mall_inventory_reservation WHERE order_no = #{orderNo} AND sku_id = #{skuId} LIMIT 1")
    InventoryReservationEntity findByOrderAndSku(@Param("orderNo") String orderNo, @Param("skuId") Long skuId);

    @Select("SELECT * FROM mall_inventory_reservation WHERE order_no = #{orderNo} AND product_id = #{productId}")
    InventoryReservationEntity findByOrderAndProduct(@Param("orderNo") String orderNo,
                                                     @Param("productId") Long productId);

    @Select("SELECT * FROM mall_inventory_reservation WHERE order_no = #{orderNo} AND status = 'LOCKED'")
    List<InventoryReservationEntity> findLockedByOrderNo(@Param("orderNo") String orderNo);

    @Update("UPDATE mall_inventory_reservation SET status = 'RELEASED', updated_at = NOW() WHERE id = #{id} AND status = 'LOCKED'")
    int markReleased(@Param("id") Long id);

    @Select("SELECT * FROM mall_inventory_reservation WHERE order_no = #{orderNo} ORDER BY product_id FOR UPDATE")
    List<InventoryReservationEntity> findByOrderForUpdate(@Param("orderNo") String orderNo);

    @Update("UPDATE mall_inventory_reservation SET status = 'CONFIRMED', updated_at = NOW() "
            + "WHERE id = #{id} AND status = 'LOCKED'")
    int markConfirmed(@Param("id") Long id);
}
