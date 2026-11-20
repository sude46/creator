package com.creator.video.infrastructure;

import com.creator.video.domain.ContentAppeal;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AppealMapper {
    String COLS = "a.id, a.appellant_id, appellant.username AS appellant_name, a.target_type, a.target_id,"
            + " (SELECT video.title FROM video WHERE video.id = a.target_id) AS target_title, a.reason,"
            + " a.status, a.resolver_id, a.resolution_reason, a.created_at, a.resolved_at";
    String FROM = " FROM content_appeal a JOIN user_account appellant ON appellant.id = a.appellant_id";
    String FILTER = " WHERE 1=1"
            + "<if test='appellantId != null'> AND a.appellant_id = #{appellantId}</if>"
            + "<if test='targetType != null'> AND a.target_type = #{targetType}</if>"
            + "<if test='targetId != null'> AND a.target_id = #{targetId}</if>"
            + "<if test='status != null'> AND a.status = #{status}</if>";

    @Insert("INSERT INTO content_appeal (appellant_id, target_type, target_id, reason) VALUES (#{appellantId}, #{targetType}, #{targetId}, #{reason})")
    void add(@Param("appellantId") long appellantId, @Param("targetType") String targetType,
            @Param("targetId") long targetId, @Param("reason") String reason);

    @Select("SELECT COUNT(*) > 0 FROM content_appeal WHERE appellant_id = #{appellantId}"
            + " AND target_type = #{targetType} AND target_id = #{targetId} AND status = 'OPEN'")
    boolean openExists(@Param("appellantId") long appellantId, @Param("targetType") String targetType,
            @Param("targetId") long targetId);

    @Select("<script>SELECT " + COLS + FROM + FILTER
            + " ORDER BY a.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<ContentAppeal> page(@Param("appellantId") Long appellantId, @Param("targetType") String targetType,
            @Param("targetId") Long targetId, @Param("status") String status,
            @Param("limit") int limit, @Param("offset") int offset);

    @Select("<script>SELECT COUNT(*)" + FROM + FILTER + "</script>")
    long count(@Param("appellantId") Long appellantId, @Param("targetType") String targetType,
            @Param("targetId") Long targetId, @Param("status") String status);

    @Select("SELECT " + COLS + FROM + " WHERE a.id = #{id} FOR UPDATE")
    ContentAppeal lock(long id);

    @Update("UPDATE content_appeal SET status = #{status}, resolver_id = #{resolverId}, resolution_reason = #{reason},"
            + " resolved_at = CURRENT_TIMESTAMP(6) WHERE id = #{id} AND status = 'OPEN'")
    int resolve(@Param("id") long id, @Param("resolverId") long resolverId, @Param("status") String status,
            @Param("reason") String reason);
}
