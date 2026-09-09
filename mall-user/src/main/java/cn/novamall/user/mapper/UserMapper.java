package cn.novamall.user.mapper;

import cn.novamall.user.entity.UserEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

    @Select("SELECT id, username, password_hash, nickname, role, status FROM mall_user WHERE username = #{username} LIMIT 1")
    UserEntity findByUsername(String username);
}
