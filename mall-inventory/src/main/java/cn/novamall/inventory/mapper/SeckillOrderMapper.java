package cn.novamall.inventory.mapper;

import cn.novamall.inventory.entity.SeckillOrderEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SeckillOrderMapper extends BaseMapper<SeckillOrderEntity> {
    @Select("SELECT * FROM mall_seckill_order WHERE seckill_no=#{no} LIMIT 1")
    SeckillOrderEntity findByNo(@Param("no") String no);
    @Select("SELECT * FROM mall_seckill_order WHERE user_id=#{userId} AND product_id=#{productId} LIMIT 1")
    SeckillOrderEntity findByUserProduct(@Param("userId") Long userId, @Param("productId") Long productId);
}
