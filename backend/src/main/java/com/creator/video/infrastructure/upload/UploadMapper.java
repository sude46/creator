package com.creator.video.infrastructure.upload;

import com.creator.video.domain.UploadAttempt;
import com.creator.video.domain.UploadAttemptState;
import com.creator.video.domain.UploadChunk;
import com.creator.video.domain.UploadVideo;
import com.creator.video.infrastructure.DraftMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UploadMapper {
    String VIDEO_COLUMNS = "id, version, source_asset_id, processing_status, (" + DraftMapper.DRAFT_CONDITION + ") AS writable";
    String ATTEMPT_COLUMNS = "id, video_id, owner_id, object_key, state, expires_at, declared_size, chunk_size, fingerprint, received_bytes";

    @Select("SELECT " + VIDEO_COLUMNS + " FROM video WHERE id = #{videoId} AND owner_id = #{ownerId} AND deleted_at IS NULL")
    UploadVideo findOwned(@Param("ownerId") long ownerId, @Param("videoId") long videoId);

    @Select("SELECT " + VIDEO_COLUMNS + " FROM video WHERE id = #{videoId} AND owner_id = #{ownerId} AND deleted_at IS NULL FOR UPDATE")
    UploadVideo lockOwned(@Param("ownerId") long ownerId, @Param("videoId") long videoId);

    @Select("SELECT id FROM user_account WHERE id = #{id} AND status = 'ACTIVE' AND role = #{role} FOR UPDATE")
    Long lockActiveOwner(@Param("id") long id, @Param("role") String role);

    @Select("SELECT COALESCE(SUM(asset.size_bytes), 0) FROM media_asset asset JOIN video ON video.id = asset.video_id WHERE video.owner_id = #{ownerId}")
    long usedBytes(long ownerId);

    @Select("SELECT COUNT(*) FROM video WHERE owner_id = #{ownerId} AND source_asset_id IS NOT NULL AND deleted_at IS NULL AND processing_status IN ('PENDING', 'PROCESSING')")
    long pendingCount(long ownerId);

    @Select("SELECT COUNT(*) FROM upload_attempt WHERE owner_id = #{ownerId} AND state IN ('UPLOADING', 'CLEANING')")
    long activeAttempts(long ownerId);

    @Insert("INSERT INTO upload_attempt (id, video_id, owner_id, object_key, state, expires_at, declared_size, chunk_size, fingerprint, received_bytes)"
            + " VALUES (#{id}, #{videoId}, #{ownerId}, #{objectKey}, #{state}, #{expiresAt}, #{declaredSize}, #{chunkSize}, #{fingerprint}, #{receivedBytes})")
    void insertAttempt(UploadAttempt attempt);

    @Select("SELECT " + ATTEMPT_COLUMNS + " FROM upload_attempt WHERE id = #{id}")
    UploadAttempt findAttempt(String id);

    @Select("SELECT " + ATTEMPT_COLUMNS + " FROM upload_attempt WHERE owner_id = #{ownerId} AND video_id = #{videoId}"
            + " AND state = 'UPLOADING' AND declared_size IS NOT NULL AND expires_at > #{now} ORDER BY created_at DESC LIMIT 1")
    UploadAttempt findActiveAttempt(@Param("ownerId") long ownerId, @Param("videoId") long videoId,
            @Param("now") LocalDateTime now);

    @Select("SELECT " + ATTEMPT_COLUMNS + " FROM upload_attempt WHERE id = #{id} FOR UPDATE")
    UploadAttempt lockAttempt(String id);

    @Select("SELECT chunk_index, size_bytes FROM upload_chunk WHERE attempt_id = #{attemptId} ORDER BY chunk_index")
    List<UploadChunk> chunks(String attemptId);

    @Insert("INSERT INTO upload_chunk (attempt_id, chunk_index, size_bytes) VALUES (#{attemptId}, #{index}, #{size})")
    void insertChunk(@Param("attemptId") String attemptId, @Param("index") int index, @Param("size") int size);

    @Update("UPDATE upload_attempt SET received_bytes = received_bytes + #{size} WHERE id = #{attemptId}")
    void addReceivedBytes(@Param("attemptId") String attemptId, @Param("size") int size);

    @Delete("DELETE FROM upload_chunk WHERE attempt_id = #{attemptId}")
    void deleteChunks(@Param("attemptId") String attemptId);

    @Insert("INSERT INTO media_asset (video_id, kind, object_key, size_bytes) VALUES (#{videoId}, 'SOURCE', #{key}, #{size})")
    void insertAsset(@Param("videoId") long videoId, @Param("key") String key, @Param("size") long size);

    @Select("SELECT id FROM media_asset WHERE object_key = #{key}")
    long assetId(String key);

    @Update("UPDATE video SET source_asset_id = #{assetId}, version = version + 1, updated_at = CURRENT_TIMESTAMP(6) WHERE id = #{videoId} AND owner_id = #{ownerId} AND version = #{version} AND " + DraftMapper.DRAFT_CONDITION)
    int bindSource(@Param("videoId") long videoId, @Param("ownerId") long ownerId, @Param("assetId") long assetId, @Param("version") long version);

    @Insert("INSERT INTO processing_task (video_id, generation) VALUES (#{videoId}, 1)")
    void insertTask(long videoId);

    @Select("SELECT COUNT(*) FROM media_asset WHERE object_key = #{key}")
    int referenceCount(String key);

    @Select("SELECT id FROM upload_attempt WHERE state IN ('UPLOADING', 'CLEANING') AND expires_at <= #{now} ORDER BY expires_at, id LIMIT #{limit}")
    List<String> expiredAttempts(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("UPDATE upload_attempt SET state = #{state}, expires_at = #{expiresAt} WHERE id = #{id}")
    void setAttemptState(@Param("id") String id, @Param("state") UploadAttemptState state, @Param("expiresAt") LocalDateTime expiresAt);
}
