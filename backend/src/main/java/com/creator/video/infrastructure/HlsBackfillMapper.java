package com.creator.video.infrastructure;

import com.creator.video.domain.HlsBackfillRepository;
import com.creator.video.domain.HlsVariant;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface HlsBackfillMapper {
    @Select("""
            SELECT v.id FROM video v
            JOIN media_asset asset ON asset.id = v.source_asset_id AND asset.kind = 'SOURCE'
            WHERE v.deleted_at IS NULL AND v.processing_status = 'READY'
              AND NOT EXISTS (SELECT 1 FROM video_hls hls WHERE hls.video_id = v.id)
              AND NOT EXISTS (SELECT 1 FROM hls_backfill_task task WHERE task.video_id = v.id
                  AND task.state IN ('PENDING', 'RUNNING', 'RETRY_WAIT'))
            ORDER BY v.id LIMIT #{limit}
            """)
    List<Long> eligible(@Param("limit") int limit);

    @Select("""
            SELECT v.id FROM video v
            JOIN media_asset asset ON asset.id = v.source_asset_id AND asset.kind = 'SOURCE'
            WHERE v.id = #{videoId} AND v.deleted_at IS NULL AND v.processing_status = 'READY'
              AND NOT EXISTS (SELECT 1 FROM video_hls hls WHERE hls.video_id = v.id)
              AND NOT EXISTS (SELECT 1 FROM hls_backfill_task task WHERE task.video_id = v.id
                  AND task.state IN ('PENDING', 'RUNNING', 'RETRY_WAIT'))
            """)
    List<Long> eligibleVideo(@Param("videoId") long videoId);

    @Select("SELECT state FROM hls_backfill_task WHERE video_id = #{videoId}")
    String state(long videoId);

    @Insert("INSERT INTO hls_backfill_task (video_id) VALUES (#{videoId})")
    void insert(long videoId);

    @Update("""
            UPDATE hls_backfill_task SET state = 'PENDING', attempts = 0, execution_token = NULL,
                lease_until = NULL, next_attempt_at = CURRENT_TIMESTAMP(6), last_error = NULL,
                updated_at = CURRENT_TIMESTAMP(6)
            WHERE video_id = #{videoId} AND state IN ('FAILED', 'DONE')
            """)
    void resetFailed(long videoId);

    @Select("""
            SELECT task.id, task.video_id, video.processing_generation, source.object_key AS source_key,
                COALESCE(source.size_bytes, 0) AS source_bytes, task.attempts, task.execution_token AS token
            FROM hls_backfill_task task
            JOIN video ON video.id = task.video_id
            LEFT JOIN media_asset source ON source.id = video.source_asset_id AND source.kind = 'SOURCE'
            WHERE video.deleted_at IS NULL AND video.processing_status = 'READY'
              AND ((task.state IN ('PENDING', 'RETRY_WAIT') AND task.next_attempt_at <= #{now})
                OR (task.state = 'RUNNING' AND task.lease_until <= #{now}))
            ORDER BY task.id LIMIT 1 FOR UPDATE
            """)
    HlsBackfillRepository.Task lockNext(@Param("now") LocalDateTime now);

    @Update("""
            UPDATE hls_backfill_task SET state = 'RUNNING', execution_token = #{token}, lease_until = #{leaseUntil},
                attempts = attempts + 1, updated_at = CURRENT_TIMESTAMP(6)
            WHERE id = #{id}
            """)
    void markRunning(@Param("id") long id, @Param("token") String token, @Param("leaseUntil") LocalDateTime leaseUntil);

    @Select("""
            SELECT id FROM hls_backfill_task WHERE id = #{id} AND execution_token = #{token}
                AND state = 'RUNNING' AND lease_until > #{now} FOR UPDATE
            """)
    Long lockOwned(@Param("id") long id, @Param("token") String token, @Param("now") LocalDateTime now);

    @Select("""
            SELECT id FROM video WHERE id = #{videoId} AND deleted_at IS NULL
                AND processing_status = 'READY' FOR UPDATE
            """)
    Long lockReady(long videoId);

    @Select("SELECT COUNT(*) FROM video_hls WHERE video_id = #{videoId}")
    long hlsCount(long videoId);

    @Insert("INSERT INTO video_hls (video_id,rendition,bandwidth,width,height,base_key,playlist_key,playlist)"
            + " VALUES (#{videoId},#{variant.name},#{variant.bandwidth},#{variant.width},#{variant.height},#{variant.baseKey},#{variant.playlistKey},#{variant.playlist})")
    void insertHls(@Param("videoId") long videoId, @Param("variant") HlsVariant variant);

    @Insert("INSERT INTO video_hls_segment (video_id,object_key) VALUES (#{videoId},#{key})")
    void insertHlsSegment(@Param("videoId") long videoId, @Param("key") String key);

    @Update("""
            UPDATE hls_backfill_task SET state = 'DONE', execution_token = NULL, lease_until = NULL,
                last_error = NULL, updated_at = CURRENT_TIMESTAMP(6)
            WHERE id = #{id} AND execution_token = #{token} AND state = 'RUNNING'
            """)
    int done(@Param("id") long id, @Param("token") String token);

    @Update("""
            UPDATE hls_backfill_task SET state = 'RETRY_WAIT', execution_token = NULL, lease_until = NULL,
                next_attempt_at = #{nextAttemptAt}, last_error = #{error}, updated_at = CURRENT_TIMESTAMP(6)
            WHERE id = #{id} AND execution_token = #{token} AND state = 'RUNNING'
            """)
    int retry(@Param("id") long id, @Param("token") String token, @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
            @Param("error") String error);

    @Update("""
            UPDATE hls_backfill_task SET state = 'FAILED', execution_token = NULL, lease_until = NULL,
                last_error = #{error}, updated_at = CURRENT_TIMESTAMP(6)
            WHERE id = #{id} AND execution_token = #{token} AND state = 'RUNNING'
            """)
    int fail(@Param("id") long id, @Param("token") String token, @Param("error") String error);

    @Select("SELECT state, COUNT(*) AS count FROM hls_backfill_task GROUP BY state")
    List<HlsBackfillRepository.StateCount> counts();
}
