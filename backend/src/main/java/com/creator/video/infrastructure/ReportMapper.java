package com.creator.video.infrastructure;

import com.creator.video.domain.ContentReport;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ReportMapper {
    String COLS = "r.id, r.reporter_id, reporter.username AS reporter_name, r.target_type, r.target_id, r.reason,"
            + " r.status, r.resolver_id, r.resolution_reason, r.created_at, r.resolved_at";
    String FROM = " FROM content_report r JOIN user_account reporter ON reporter.id=r.reporter_id";
    @Insert("INSERT INTO content_report (reporter_id,target_type,target_id,reason) VALUES (#{reporterId},#{targetType},#{targetId},#{reason})")
    void add(@Param("reporterId") long reporterId, @Param("targetType") String targetType,
            @Param("targetId") long targetId, @Param("reason") String reason);
    @Select("<script>SELECT " + COLS + FROM + " WHERE 1=1 <if test='status != null'> AND r.status=#{status}</if>"
            + " ORDER BY r.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<ContentReport> page(@Param("status") String status, @Param("limit") int limit, @Param("offset") int offset);
    @Select("<script>SELECT COUNT(*)" + FROM + " WHERE 1=1 <if test='status != null'> AND r.status=#{status}</if></script>")
    long count(@Param("status") String status);
    @Select("SELECT " + COLS + FROM + " WHERE r.id=#{id} FOR UPDATE")
    ContentReport lock(long id);
    @Update("UPDATE content_report SET status=#{status}, resolver_id=#{resolverId}, resolution_reason=#{reason},"
            + " resolved_at=CURRENT_TIMESTAMP(6) WHERE id=#{id} AND status='OPEN'")
    int resolve(@Param("id") long id, @Param("resolverId") long resolverId, @Param("status") String status,
            @Param("reason") String reason);
}
