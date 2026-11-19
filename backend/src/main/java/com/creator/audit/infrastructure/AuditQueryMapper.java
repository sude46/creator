package com.creator.audit.infrastructure;

import com.creator.audit.domain.AuditRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AuditQueryMapper {
    String WHERE = """
            WHERE 1 = 1
            <if test="actorId != null"> AND actor_id = #{actorId}</if>
            <if test="action != null"> AND action = #{action}</if>
            <if test="targetType != null"> AND target_type = #{targetType}</if>
            <if test="from != null"> AND created_at &gt;= #{from}</if>
            <if test="to != null"> AND created_at &lt;= #{to}</if>
            """;

    @Select("<script>SELECT id, actor_id, action, target_type, target_id, reason, request_id, created_at "
            + "FROM operation_log " + WHERE + " ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<AuditRecord> findPage(@Param("actorId") Long actorId, @Param("action") String action,
            @Param("targetType") String targetType, @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to, @Param("limit") int limit, @Param("offset") int offset);

    @Select("<script>SELECT COUNT(*) FROM operation_log " + WHERE + "</script>")
    long count(@Param("actorId") Long actorId, @Param("action") String action,
            @Param("targetType") String targetType, @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
