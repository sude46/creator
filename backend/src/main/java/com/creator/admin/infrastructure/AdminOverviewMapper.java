package com.creator.admin.infrastructure;

import com.creator.admin.domain.AdminOverview;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminOverviewMapper {
    @Select("""
            SELECT COUNT(*) FROM video
            WHERE review_status = 'PENDING' AND deleted_at IS NULL AND owner_id <> #{adminId}
            """)
    long pendingReviews(@Param("adminId") long adminId);

    @Select("SELECT COUNT(*) FROM video WHERE processing_status = 'FAILED' AND deleted_at IS NULL")
    long failedProcessing();

    @Select("SELECT COUNT(*) FROM user_account WHERE created_at >= #{start} AND status <> 'DELETED'")
    long usersCreatedSince(@Param("start") LocalDateTime start);

    @Select("SELECT COUNT(*) FROM review_record WHERE created_at >= #{start}")
    long decisionsSince(@Param("start") LocalDateTime start);

    @Select("""
            SELECT id, video_id, reviewer_id, outcome, reason, title, created_at
            FROM review_record ORDER BY id DESC LIMIT #{limit}
            """)
    List<AdminOverview.RecentDecision> recentDecisions(@Param("limit") int limit);
}
