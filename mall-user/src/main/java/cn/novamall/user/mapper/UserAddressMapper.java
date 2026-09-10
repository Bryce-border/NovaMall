package cn.novamall.user.mapper;

import cn.novamall.user.entity.UserAddressEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserAddressMapper extends BaseMapper<UserAddressEntity> {
    @Update("UPDATE mall_user_address SET is_default = 0, updated_at = NOW() WHERE user_id = #{userId} AND deleted = 0")
    int clearDefault(Long userId);
}
