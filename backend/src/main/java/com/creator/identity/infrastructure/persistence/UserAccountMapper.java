package com.creator.identity.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccountRow> {
    @Select("SELECT id, username, password_hash, role, status FROM user_account WHERE username = LOWER(#{username})")
    UserAccountRow findCredentials(@Param("username") String username);

    @Select("SELECT id, username, role, status FROM user_account WHERE username = LOWER(#{username})")
    AccountSummaryRow findSummary(@Param("username") String username);

    @Select("""
            SELECT id, username, role, status FROM user_account
            WHERE status <> 'DELETED'
            ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}
            """)
    List<AccountSummaryRow> findPage(@Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM user_account WHERE status <> 'DELETED'")
    long count();

    @Select("SELECT id, username, role, status FROM user_account WHERE id = #{id}")
    AccountSummaryRow findById(long id);

    @Update("""
            UPDATE user_account SET status = #{status}, updated_at = CURRENT_TIMESTAMP(6)
            WHERE id = #{id} AND role = 'USER' AND status <> 'DELETED'
            """)
    int updateUserStatus(@Param("id") long id, @Param("status") String status);
}
