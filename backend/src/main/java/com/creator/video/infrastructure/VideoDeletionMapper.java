package com.creator.video.infrastructure;

import com.creator.video.domain.VideoDeletion;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface VideoDeletionMapper {
    @Insert("""
            INSERT INTO video_deletion (video_id, owner_id, reason, state, next_attempt_at)
            VALUES (#{videoId}, #{ownerId}, #{reason}, 'PENDING', CURRENT_TIMESTAMP(6))
            """)
    void insert(@Param("videoId") long videoId, @Param("ownerId") long ownerId, @Param("reason") String reason);

    @Select("""
            SELECT id, video_id, owner_id, state, attempts, next_attempt_at, last_error
            FROM video_deletion
            WHERE state IN ('PENDING', 'CLEANING') AND next_attempt_at <= #{now}
            ORDER BY next_attempt_at, id LIMIT #{limit}
            """)
    List<VideoDeletion> due(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("""
            UPDATE video_deletion SET state = 'CLEANING', attempts = attempts + 1,
                next_attempt_at = #{leaseUntil}, updated_at = CURRENT_TIMESTAMP(6)
            WHERE id = #{id} AND state IN ('PENDING', 'CLEANING') AND next_attempt_at <= #{now}
            """)
    int claim(@Param("id") long id, @Param("now") LocalDateTime now, @Param("leaseUntil") LocalDateTime leaseUntil);

    @Select("""
            SELECT object_key FROM media_asset WHERE video_id = #{videoId}
            UNION ALL
            SELECT object_key FROM video_hls_segment WHERE video_id = #{videoId}
            UNION ALL
            SELECT playlist_key FROM video_hls WHERE video_id = #{videoId}
            """)
    List<String> objectKeys(long videoId);

    @Update("UPDATE video SET source_asset_id = NULL WHERE id = #{videoId}")
    void clearSource(long videoId);

    @Delete("DELETE FROM media_asset WHERE video_id = #{videoId}")
    void deleteAssets(long videoId);

    @Delete("DELETE FROM video_hls_segment WHERE video_id = #{videoId}")
    void deleteHlsSegments(long videoId);

    @Delete("DELETE FROM video_hls WHERE video_id = #{videoId}")
    void deleteHls(long videoId);

    @Update("""
            UPDATE video_deletion SET state = 'DONE', last_error = NULL, updated_at = CURRENT_TIMESTAMP(6)
            WHERE id = #{id} AND state = 'CLEANING'
            """)
    int done(long id);

    @Update("""
            UPDATE video_deletion SET state = 'PENDING', next_attempt_at = #{nextAttemptAt},
                last_error = #{error}, updated_at = CURRENT_TIMESTAMP(6)
            WHERE id = #{id} AND state = 'CLEANING'
            """)
    int retry(@Param("id") long id, @Param("nextAttemptAt") LocalDateTime nextAttemptAt, @Param("error") String error);
}
