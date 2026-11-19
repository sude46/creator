package com.creator.video.infrastructure;

import com.creator.video.domain.CreatorVideo;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CreatorVideoMapper {
    String COLUMNS = "v.id, v.owner_id, v.category_id, v.title, v.description, v.version, v.processing_generation,"
            + " (v.source_asset_id IS NOT NULL) AS source_uploaded, v.moderation_blocked, v.processing_status,"
            + " v.review_status, v.publication_status, task.state AS task_state, task.failure_code,"
            + " COALESCE(task.execution_attempts, 0) AS execution_attempts, task.next_attempt_at,"
            + " v.duration_millis, v.media_width, v.media_height, v.review_round, v.review_submitted_at, v.published_at";
    String FROM = " FROM video v LEFT JOIN processing_task task ON task.video_id = v.id AND task.generation = v.processing_generation";
    String OWNED = " WHERE v.owner_id = #{ownerId} AND v.deleted_at IS NULL";

    @Select("SELECT " + COLUMNS + FROM + OWNED + " AND v.id = #{videoId}")
    CreatorVideo findOwned(@Param("ownerId") long ownerId, @Param("videoId") long videoId);

    @Select("SELECT " + COLUMNS + FROM + OWNED + " AND v.id = #{videoId} FOR UPDATE")
    CreatorVideo lockOwned(@Param("ownerId") long ownerId, @Param("videoId") long videoId);

    @Select("SELECT " + COLUMNS + FROM + " WHERE v.id = #{videoId}")
    CreatorVideo findAny(@Param("videoId") long videoId);

    @Select("SELECT " + COLUMNS + FROM + " WHERE v.id = #{videoId} FOR UPDATE")
    CreatorVideo lockAny(@Param("videoId") long videoId);

    @Update("""
            <script>
            UPDATE video SET deleted_at = CURRENT_TIMESTAMP(6), publication_status = 'UNPUBLISHED',
                published_at = NULL, version = version + 1, updated_at = CURRENT_TIMESTAMP(6)
            WHERE id = #{videoId} AND version = #{version} AND deleted_at IS NULL
            <if test="ownerId != null">AND owner_id = #{ownerId}</if>
            </script>
            """)
    int softDelete(@Param("videoId") long videoId, @Param("ownerId") Long ownerId, @Param("version") long version);

    @Update("""
            UPDATE processing_task SET state = 'CANCELLED', next_attempt_at = CURRENT_TIMESTAMP(6),
                next_dispatch_at = CURRENT_TIMESTAMP(6), execution_token = NULL, execution_lease_until = NULL,
                dispatch_token = NULL, dispatch_lease_until = NULL
            WHERE video_id = #{videoId} AND state IN ('PENDING', 'RUNNING', 'RETRY_WAIT')
            """)
    void cancelActiveTasks(long videoId);

    @Select("SELECT " + COLUMNS + FROM + OWNED + " ORDER BY v.id DESC LIMIT #{limit} OFFSET #{offset}")
    List<CreatorVideo> findPage(@Param("ownerId") long ownerId, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM video WHERE owner_id = #{ownerId} AND deleted_at IS NULL")
    long count(long ownerId);

    @Update("""
            UPDATE video SET processing_generation = processing_generation + 1, processing_status = 'PENDING',
            duration_millis = NULL, media_width = NULL, media_height = NULL, version = version + 1,
            updated_at = CURRENT_TIMESTAMP(6)
            WHERE id = #{videoId} AND owner_id = #{ownerId} AND version = #{version}
            AND processing_generation = #{generation} AND processing_status = 'FAILED'
            AND review_status = 'NOT_SUBMITTED' AND publication_status = 'UNPUBLISHED'
            AND source_asset_id IS NOT NULL AND deleted_at IS NULL AND moderation_blocked = FALSE
            AND NOT EXISTS (SELECT 1 FROM processing_task task WHERE task.video_id = video.id
                AND task.state IN ('PENDING', 'RUNNING', 'RETRY_WAIT'))
            """)
    int retry(@Param("ownerId") long ownerId, @Param("videoId") long videoId, @Param("version") long version,
            @Param("generation") long generation);

    @Insert("INSERT INTO processing_task (video_id, generation) VALUES (#{videoId}, #{generation})")
    void insertTask(@Param("videoId") long videoId, @Param("generation") long generation);
}
