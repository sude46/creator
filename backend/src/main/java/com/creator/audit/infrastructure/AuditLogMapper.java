package com.creator.audit.infrastructure;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuditLogMapper {
    @Insert("""
            INSERT INTO operation_log (actor_id, action, target_type, target_id, request_id, reason)
            VALUES (#{actorId}, #{action}, #{targetType}, #{targetId}, #{requestId}, #{reason})
            """)
    void insert(@Param("actorId") long actorId, @Param("action") String action,
            @Param("targetType") String targetType, @Param("targetId") long targetId,
            @Param("requestId") String requestId, @Param("reason") String reason);
}
