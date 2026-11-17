package com.creator.video.infrastructure;

import com.creator.video.domain.*;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PublicationMapper {
    String COMPLETE = "processing_status = 'READY' AND deleted_at IS NULL AND duration_millis IS NOT NULL"
            + " AND media_width IS NOT NULL AND media_height IS NOT NULL"
            + " AND EXISTS (SELECT 1 FROM media_asset source WHERE source.id = video.source_asset_id AND source.video_id = video.id AND source.kind = 'SOURCE')"
            + " AND EXISTS (SELECT 1 FROM media_asset cover WHERE cover.video_id = video.id AND cover.kind = 'COVER')"
            + " AND EXISTS (SELECT 1 FROM processing_task task WHERE task.video_id = video.id AND task.generation = video.processing_generation AND task.state = 'SUCCEEDED')";
    String HISTORY = " FROM operation_log WHERE target_type = 'VIDEO' AND target_id = #{videoId}"
            + " AND action IN ('VIDEO_BLOCKED', 'VIDEO_UNBLOCKED')";

    @Select("SELECT " + CreatorVideoMapper.COLUMNS + CreatorVideoMapper.FROM + " WHERE v.id = #{videoId} AND v.deleted_at IS NULL")
    CreatorVideo find(long videoId);

    @Select("SELECT " + CreatorVideoMapper.COLUMNS + CreatorVideoMapper.FROM + " WHERE v.id = #{videoId} AND v.deleted_at IS NULL FOR UPDATE")
    CreatorVideo lock(long videoId);

    @Select("SELECT " + CreatorVideoMapper.COLUMNS + CreatorVideoMapper.FROM
            + " WHERE v.processing_status = 'READY' AND v.deleted_at IS NULL ORDER BY v.id DESC LIMIT #{limit} OFFSET #{offset}")
    List<CreatorVideo> managed(@Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM video WHERE processing_status = 'READY' AND deleted_at IS NULL")
    long managedCount();

    @Update("UPDATE video SET publication_status = 'OFFLINE', published_at = NULL, version = version + 1, updated_at = CURRENT_TIMESTAMP(6)"
            + " WHERE id = #{videoId} AND owner_id = #{ownerId} AND version = #{version} AND review_status = 'APPROVED'"
            + " AND publication_status = 'PUBLISHED' AND moderation_blocked = FALSE AND " + COMPLETE)
    int offline(@Param("ownerId") long ownerId, @Param("videoId") long videoId, @Param("version") long version);

    @Update("UPDATE video SET publication_status = 'PUBLISHED', published_at = #{now}, version = version + 1, updated_at = #{now}"
            + " WHERE id = #{videoId} AND owner_id = #{ownerId} AND version = #{version} AND review_status = 'APPROVED'"
            + " AND publication_status = 'OFFLINE' AND moderation_blocked = FALSE AND " + COMPLETE)
    int publish(@Param("ownerId") long ownerId, @Param("videoId") long videoId, @Param("version") long version, @Param("now") LocalDateTime now);

    @Update("UPDATE video SET moderation_blocked = TRUE, publication_status = 'OFFLINE', published_at = NULL,"
            + " version = version + 1, updated_at = CURRENT_TIMESTAMP(6) WHERE id = #{videoId} AND version = #{version}"
            + " AND moderation_blocked = FALSE AND " + COMPLETE)
    int block(@Param("videoId") long videoId, @Param("version") long version);

    @Update("UPDATE video SET moderation_blocked = FALSE, publication_status = 'OFFLINE', review_status = 'NOT_SUBMITTED',"
            + " published_at = NULL, review_submitted_at = NULL, version = version + 1, updated_at = CURRENT_TIMESTAMP(6)"
            + " WHERE id = #{videoId} AND version = #{version} AND moderation_blocked = TRUE AND " + COMPLETE)
    int unblock(@Param("videoId") long videoId, @Param("version") long version);

    @Select("SELECT id, actor_id, action, reason, created_at" + HISTORY + " ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<ModerationRecord> history(@Param("videoId") long videoId, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*)" + HISTORY)
    long historyCount(long videoId);
}
