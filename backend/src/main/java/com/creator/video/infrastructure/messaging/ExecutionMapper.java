package com.creator.video.infrastructure.messaging;

import com.creator.video.domain.*;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ExecutionMapper extends ExecutionRepository {
    @Override
    @Select("SELECT id, owner_id, processing_generation, source_asset_id, processing_status, moderation_blocked, deleted_at FROM video WHERE id = #{id} FOR UPDATE")
    Video lockVideo(long id);

    @Override
    @Select("SELECT id, video_id, generation, state, execution_token, execution_lease_until, execution_attempts, next_attempt_at FROM processing_task WHERE id = #{id} FOR UPDATE")
    Task lockTask(long id);

    @Override
    @Select("SELECT COUNT(*) > 0 FROM user_account WHERE id = #{id} AND status = 'ACTIVE'")
    boolean activeOwner(long id);

    @Override
    @Select("SELECT object_key, size_bytes FROM media_asset WHERE id = #{assetId} AND video_id = #{videoId} AND kind = 'SOURCE'")
    Source source(@Param("assetId") long assetId, @Param("videoId") long videoId);

    @Override
    @Update("UPDATE processing_task SET state = 'RUNNING', execution_token = #{claim.executionToken}, execution_lease_until = #{leaseUntil}, execution_attempts = execution_attempts + 1, failure_code = NULL, dispatch_token = NULL, dispatch_lease_until = NULL WHERE id = #{claim.taskId}")
    void start(@Param("claim") ExecutionClaim claim, @Param("leaseUntil") LocalDateTime leaseUntil);

    @Override
    @Insert("INSERT INTO processing_output (execution_token, task_id, object_key, expires_at) VALUES (#{claim.executionToken}, #{claim.taskId}, #{claim.coverKey}, #{expiresAt})")
    void createOutput(@Param("claim") ExecutionClaim claim, @Param("expiresAt") LocalDateTime expiresAt);

    @Override
    @Update("UPDATE video SET processing_status = #{state}, version = version + 1, updated_at = CURRENT_TIMESTAMP(6) WHERE id = #{videoId}")
    void videoState(@Param("videoId") long videoId, @Param("state") String state);

    @Override
    @Update("UPDATE processing_task SET state = #{state}, failure_code = #{failure}, next_attempt_at = #{retryAt}, next_dispatch_at = #{retryAt}, execution_token = NULL, execution_lease_until = NULL, dispatch_token = NULL, dispatch_lease_until = NULL WHERE id = #{taskId}")
    void finishTask(@Param("taskId") long taskId, @Param("state") String state,
            @Param("failure") String failure, @Param("retryAt") LocalDateTime retryAt);

    @Override
    default void bind(ExecutionClaim claim, MediaMetadata metadata, long coverBytes, List<HlsVariant> hls) {
        insertCover(claim, coverBytes);
        for (var variant : hls) {
            insertHls(claim.videoId(), variant);
            for (String key : variant.segmentKeys()) insertHlsSegment(claim.videoId(), key);
        }
        ready(claim.videoId(), metadata);
    }

    @Insert("INSERT INTO video_hls (video_id,rendition,bandwidth,width,height,base_key,playlist_key,playlist)"
            + " VALUES (#{videoId},#{variant.name},#{variant.bandwidth},#{variant.width},#{variant.height},#{variant.baseKey},#{variant.playlistKey},#{variant.playlist})")
    void insertHls(@Param("videoId") long videoId, @Param("variant") HlsVariant variant);

    @Insert("INSERT INTO video_hls_segment (video_id,object_key) VALUES (#{videoId},#{key})")
    void insertHlsSegment(@Param("videoId") long videoId, @Param("key") String key);

    @Insert("INSERT INTO media_asset (video_id, kind, object_key, size_bytes) VALUES (#{claim.videoId}, 'COVER', #{claim.coverKey}, #{bytes})")
    void insertCover(@Param("claim") ExecutionClaim claim, @Param("bytes") long bytes);

    @Update("UPDATE video SET processing_status = 'READY', duration_millis = #{metadata.durationMillis}, media_width = #{metadata.width}, media_height = #{metadata.height}, version = version + 1, updated_at = CURRENT_TIMESTAMP(6) WHERE id = #{videoId}")
    void ready(@Param("videoId") long videoId, @Param("metadata") MediaMetadata metadata);

    @Override
    @Select("SELECT 1 AS schema_version, id AS task_id, video_id, generation FROM processing_task WHERE (state = 'RUNNING' AND execution_lease_until <= #{now}) OR (state = 'RETRY_WAIT' AND next_attempt_at <= #{now}) ORDER BY id LIMIT #{limit}")
    List<ProcessingMessage> recoverable(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Override
    @Select("SELECT execution_token, task_id, object_key, state, expires_at FROM processing_output WHERE state IN ('STAGING','CLEANING') AND expires_at <= #{now} ORDER BY expires_at, execution_token LIMIT #{limit}")
    List<Output> expiredOutputs(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Override
    @Select("SELECT execution_token, task_id, object_key, state, expires_at FROM processing_output WHERE execution_token = #{token} FOR UPDATE")
    Output lockOutput(String token);

    @Override
    @Select("SELECT COUNT(*) > 0 FROM media_asset WHERE object_key = #{key}")
    boolean referenced(String key);

    @Override
    @Update("UPDATE processing_output SET state = #{state}, expires_at = #{expiresAt} WHERE execution_token = #{token}")
    void outputState(@Param("token") String token, @Param("state") String state, @Param("expiresAt") LocalDateTime expiresAt);
}
