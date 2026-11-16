package com.creator.identity.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccountRow> {
    @Select("SELECT id, username, password_hash, role, status FROM user_account WHERE username = LOWER(#{username})")
    UserAccountRow findCredentials(@Param("username") String username);

    @Select("SELECT id, username, role, status FROM user_account WHERE username = LOWER(#{username})")
    AccountSummaryRow findSummary(@Param("username") String username);
}
