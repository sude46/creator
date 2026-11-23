package com.creator.identity.infrastructure.persistence;

import com.creator.identity.domain.DeviceSession;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface DeviceSessionMapper {
    @Select("SELECT id,user_id,session_hash,session_version,ip,user_agent,created_at,last_seen_at,expires_at,revoked_at"
            + " FROM user_session WHERE id = #{id}")
    DeviceSession find(String id);

    @Select("SELECT id,user_id,session_hash,session_version,ip,user_agent,created_at,last_seen_at,expires_at,revoked_at"
            + " FROM user_session WHERE user_id = #{userId} AND revoked_at IS NULL AND expires_at > #{now}"
            + " ORDER BY last_seen_at DESC, id")
    List<DeviceSession> active(@Param("userId") long userId, @Param("now") LocalDateTime now);

    @Insert("INSERT INTO user_session (id,user_id,session_hash,session_version,ip,user_agent,created_at,last_seen_at,expires_at)"
            + " VALUES (#{id},#{userId},#{sessionHash},#{sessionVersion},#{ip},#{userAgent},#{createdAt},#{lastSeenAt},#{expiresAt})")
    void insert(DeviceSession session);

    @Update("UPDATE user_session SET last_seen_at = #{lastSeenAt}, expires_at = #{expiresAt} WHERE id = #{id}")
    void touch(@Param("id") String id, @Param("lastSeenAt") LocalDateTime lastSeenAt,
            @Param("expiresAt") LocalDateTime expiresAt);

    @Update("UPDATE user_session SET revoked_at = #{now} WHERE id = #{id} AND user_id = #{userId} AND revoked_at IS NULL")
    int revoke(@Param("id") String id, @Param("userId") long userId, @Param("now") LocalDateTime now);

    @Update("UPDATE user_session SET revoked_at = #{now} WHERE user_id = #{userId} AND id <> #{exceptId} AND revoked_at IS NULL")
    int revokeOthers(@Param("userId") long userId, @Param("exceptId") String exceptId, @Param("now") LocalDateTime now);

    @Update("UPDATE user_session SET revoked_at = #{now} WHERE user_id = #{userId} AND revoked_at IS NULL")
    int revokeAll(@Param("userId") long userId, @Param("now") LocalDateTime now);

    @Delete("DELETE FROM user_session WHERE id = #{id}")
    void delete(String id);
}
