package cn.novamall.inventory.mapper;

import cn.novamall.inventory.entity.InventoryEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InventoryMapper extends BaseMapper<InventoryEntity> {

    @Update("""
            UPDATE mall_inventory
            SET available_stock = available_stock - #{quantity},
                locked_stock = locked_stock + #{quantity},
                version = version + 1,
                updated_at = NOW()
            WHERE product_id = #{productId}
              AND version = #{version}
              AND available_stock >= #{quantity}
            """)
    int reserve(@Param("productId") Long productId, @Param("quantity") Integer quantity,
                @Param("version") Integer version);

    @Update("""
            UPDATE mall_inventory
            SET available_stock = available_stock + #{quantity},
                locked_stock = locked_stock - #{quantity},
                version = version + 1,
                updated_at = NOW()
            WHERE product_id = #{productId}
              AND locked_stock >= #{quantity}
            """)
    int release(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    @Update("""
            UPDATE mall_inventory
            SET total_stock = total_stock - #{quantity},
                locked_stock = locked_stock - #{quantity},
                version = version + 1, updated_at = NOW()
            WHERE product_id = #{productId}
              AND locked_stock >= #{quantity} AND total_stock >= #{quantity}
            """)
    int confirm(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    @Update("""
            UPDATE mall_inventory
            SET total_stock = total_stock + #{quantity},
                available_stock = available_stock + #{quantity},
                updated_at = NOW()
            WHERE product_id = #{productId}
            """)
    int increase(@Param("productId") Long productId, @Param("quantity") Integer quantity);
}
