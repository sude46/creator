package com.creator.video.infrastructure;

import com.creator.video.domain.*;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ReviewMapper {
    String READY = "processing_status = 'READY' AND source_asset_id IS NOT NULL AND duration_millis IS NOT NULL"
            + " AND media_width IS NOT NULL AND media_height IS NOT NULL AND deleted_at IS NULL AND moderation_blocked = FALSE"
            + " AND EXISTS (SELECT 1 FROM media_asset asset WHERE asset.video_id = video.id AND asset.kind = 'COVER')"
            + " AND EXISTS (SELECT 1 FROM processing_task task WHERE task.video_id = video.id"
            + " AND task.generation = video.processing_generation AND task.state = 'SUCCEEDED')";
    String PENDING = " WHERE v.review_status = 'PENDING' AND v.processing_status = 'READY' AND v.deleted_at IS NULL"
            + " AND v.moderation_blocked = FALSE AND v.owner_id <> #{reviewerId}"
            + " AND EXISTS (SELECT 1 FROM user_account owner WHERE owner.id = v.owner_id AND owner.status = 'ACTIVE')";

    @Update("UPDATE video SET title = #{metadata.title}, description = #{metadata.description}, category_id = #{metadata.categoryId},"
            + " review_status = 'NOT_SUBMITTED', publication_status = 'UNPUBLISHED', published_at = NULL, review_submitted_at = NULL,"
            + " version = version + 1, updated_at = CURRENT_TIMESTAMP(6) WHERE id = #{videoId} AND owner_id = #{ownerId}"
            + " AND version = #{version} AND review_status <> 'PENDING' AND " + READY)
    int updateMetadata(@Param("ownerId") long ownerId, @Param("videoId") long videoId, @Param("version") long version,
            @Param("metadata") VideoMetadata metadata);

    @Update("UPDATE video SET review_status = 'PENDING', review_round = review_round + 1, review_submitted_at = #{now},"
            + " publication_status = 'UNPUBLISHED', published_at = NULL, version = version + 1, updated_at = #{now}"
            + " WHERE id = #{videoId} AND owner_id = #{ownerId} AND version = #{version}"
            + " AND review_status IN ('NOT_SUBMITTED', 'REJECTED') AND publication_status <> 'PUBLISHED' AND " + READY)
    int submit(@Param("ownerId") long ownerId, @Param("videoId") long videoId, @Param("version") long version,
            @Param("now") LocalDateTime now);

    @Update("UPDATE video SET review_status = 'NOT_SUBMITTED', review_submitted_at = NULL, version = version + 1,"
            + " updated_at = CURRENT_TIMESTAMP(6) WHERE id = #{videoId} AND owner_id = #{ownerId} AND version = #{version}"
            + " AND review_status = 'PENDING' AND review_round = #{round} AND " + READY)
    int withdraw(@Param("ownerId") long ownerId, @Param("videoId") long videoId, @Param("version") long version,
            @Param("round") long round);

    @Select("SELECT " + CreatorVideoMapper.COLUMNS + CreatorVideoMapper.FROM + PENDING + " AND v.id = #{videoId}")
    CreatorVideo pending(@Param("videoId") long videoId, @Param("reviewerId") long reviewerId);

    @Select("SELECT " + CreatorVideoMapper.COLUMNS + CreatorVideoMapper.FROM + PENDING + " AND v.id = #{videoId} FOR UPDATE")
    CreatorVideo lockPending(@Param("videoId") long videoId, @Param("reviewerId") long reviewerId);

    @Select("SELECT " + CreatorVideoMapper.COLUMNS + CreatorVideoMapper.FROM + PENDING
            + " ORDER BY v.review_submitted_at, v.id LIMIT #{limit} OFFSET #{offset}")
    List<CreatorVideo> queue(@Param("reviewerId") long reviewerId, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM video v" + PENDING)
    long queueCount(long reviewerId);

    @Update("UPDATE video SET review_status = #{decision.outcome}, publication_status = #{publication}, published_at = #{publishedAt},"
            + " version = version + 1, updated_at = CURRENT_TIMESTAMP(6) WHERE id = #{videoId} AND owner_id <> #{reviewerId}"
            + " AND version = #{version} AND review_round = #{round} AND review_status = 'PENDING' AND " + READY
            + " AND EXISTS (SELECT 1 FROM user_account owner WHERE owner.id = video.owner_id AND owner.status = 'ACTIVE')")
    int decide(@Param("videoId") long videoId, @Param("reviewerId") long reviewerId, @Param("version") long version,
            @Param("round") long round, @Param("decision") ReviewDecision decision, @Param("publication") String publication,
            @Param("publishedAt") LocalDateTime publishedAt);

    @Insert("""
            INSERT INTO review_record (video_id, review_round, content_version, processing_generation, reviewer_id,
                outcome, reason, title, description, category_id)
            VALUES (#{snapshot.id}, #{snapshot.reviewRound}, #{snapshot.version}, #{snapshot.processingGeneration}, #{reviewerId},
                #{decision.outcome}, #{decision.reason}, #{snapshot.title}, #{snapshot.description}, #{snapshot.categoryId})
            """)
    void record(@Param("snapshot") CreatorVideo snapshot, @Param("reviewerId") long reviewerId, @Param("decision") ReviewDecision decision);

    @Select("SELECT COUNT(*) > 0 FROM video WHERE id = #{videoId} AND deleted_at IS NULL AND (review_status = 'PENDING' OR EXISTS (SELECT 1 FROM review_record history WHERE history.video_id = video.id))")
    boolean hasHistory(long videoId);

    @Select("SELECT id, video_id, review_round, content_version, processing_generation, reviewer_id, outcome, reason, title, description, category_id, created_at FROM review_record WHERE video_id = #{videoId} ORDER BY review_round DESC LIMIT #{limit} OFFSET #{offset}")
    List<ReviewRecord> history(@Param("videoId") long videoId, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM review_record WHERE video_id = #{videoId}")
    long historyCount(long videoId);
}
