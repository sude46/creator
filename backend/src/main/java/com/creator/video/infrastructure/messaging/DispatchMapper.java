package com.creator.video.infrastructure.messaging;

import com.creator.video.domain.DispatchClaim;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface DispatchMapper {
    String ELIGIBLE = "EXISTS (SELECT 1 FROM video v JOIN user_account a ON a.id = v.owner_id"
            + " WHERE v.id = processing_task.video_id AND a.status = 'ACTIVE' AND v.deleted_at IS NULL"
            + " AND v.moderation_blocked = FALSE AND v.source_asset_id IS NOT NULL AND v.processing_status = 'PENDING')";
    String DUE = "state = 'PENDING' AND next_dispatch_at <= #{now}"
            + " AND (dispatch_lease_until IS NULL OR dispatch_lease_until <= #{now}) AND " + ELIGIBLE;

    @Select("SELECT id FROM processing_task WHERE " + DUE + " ORDER BY next_dispatch_at, id LIMIT #{limit}")
    List<Long> dueIds(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("UPDATE processing_task SET dispatch_token = #{token}, dispatch_lease_until = #{leaseUntil},"
            + " dispatch_attempts = dispatch_attempts + 1 WHERE id = #{id} AND " + DUE)
    int claim(@Param("id") long id, @Param("token") String token, @Param("now") LocalDateTime now,
            @Param("leaseUntil") LocalDateTime leaseUntil);

    @Select("SELECT id, video_id, generation, dispatch_token, dispatch_attempts FROM processing_task WHERE id = #{id}")
    DispatchClaim claimed(long id);

    @Update("""
            UPDATE processing_task SET published_at = #{now}, next_dispatch_at = #{redriveAt},
            dispatch_token = NULL, dispatch_lease_until = NULL, last_dispatch_error = NULL
            WHERE id = #{id} AND state = 'PENDING' AND dispatch_token = #{token} AND dispatch_lease_until > #{now}
            """)
    int markPublished(@Param("id") long id, @Param("token") String token, @Param("now") LocalDateTime now,
            @Param("redriveAt") LocalDateTime redriveAt);

    @Update("""
            UPDATE processing_task SET next_dispatch_at = #{retryAt}, last_dispatch_error = #{error},
            dispatch_token = NULL, dispatch_lease_until = NULL
            WHERE id = #{id} AND state = 'PENDING' AND dispatch_token = #{token} AND dispatch_lease_until > #{now}
            """)
    int markFailed(@Param("id") long id, @Param("token") String token, @Param("now") LocalDateTime now,
            @Param("retryAt") LocalDateTime retryAt, @Param("error") String error);
}
