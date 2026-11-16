package com.creator.identity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccount> {
    @Select("SELECT id, username, password_hash, role, status FROM user_account WHERE username = LOWER(#{username})")
    UserAccount findByUsername(@Param("username") String username);
}
