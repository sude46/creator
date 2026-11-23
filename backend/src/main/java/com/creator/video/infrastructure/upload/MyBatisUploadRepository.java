package com.creator.video.infrastructure.upload;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.identity.domain.Actor;
import com.creator.video.domain.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisUploadRepository implements UploadRepository {
    private final UploadMapper mapper;
    public MyBatisUploadRepository(UploadMapper mapper) { this.mapper = mapper; }
    @Override
    public Optional<UploadVideo> findOwned(long ownerId, long videoId, boolean lock) {
        return Optional.ofNullable(lock ? mapper.lockOwned(ownerId, videoId) : mapper.findOwned(ownerId, videoId));
    }
    @Override
    public boolean lockActiveOwner(Actor actor) { return mapper.lockActiveOwner(actor.id(), actor.role().name()) != null; }
    @Override
    public long usedBytes(long ownerId) { return mapper.usedBytes(ownerId); }
    @Override
    public long pendingCount(long ownerId) { return mapper.pendingCount(ownerId); }
    @Override
    public long activeAttempts(long ownerId) { return mapper.activeAttempts(ownerId); }
    @Override
    public void insertAttempt(UploadAttempt attempt) { mapper.insertAttempt(attempt); }
    @Override
    public Optional<UploadAttempt> findAttempt(String id) { return Optional.ofNullable(mapper.findAttempt(id)); }
    @Override
    public Optional<UploadAttempt> findActiveAttempt(long ownerId, long videoId, LocalDateTime now) {
        return Optional.ofNullable(mapper.findActiveAttempt(ownerId, videoId, now));
    }
    @Override
    public Optional<UploadAttempt> lockAttempt(String id) { return Optional.ofNullable(mapper.lockAttempt(id)); }
    @Override
    public List<UploadChunk> chunks(String attemptId) { return mapper.chunks(attemptId); }
    @Override
    public void insertChunk(String attemptId, int index, int size) {
        mapper.insertChunk(attemptId, index, size);
        mapper.addReceivedBytes(attemptId, size);
    }
    @Override
    public void deleteChunks(String attemptId) { mapper.deleteChunks(attemptId); }
    @Override
    public void bind(UploadAttempt attempt, long size, long version) {
        mapper.insertAsset(attempt.videoId(), attempt.objectKey(), size);
        if (mapper.bindSource(attempt.videoId(), attempt.ownerId(), mapper.assetId(attempt.objectKey()), version) != 1) {
            throw new BusinessException(ErrorCode.STALE_WRITE);
        }
        mapper.insertTask(attempt.videoId());
    }
    @Override
    public boolean hasReference(String objectKey) { return mapper.referenceCount(objectKey) > 0; }
    @Override
    public List<String> expiredAttempts(LocalDateTime now, int limit) { return mapper.expiredAttempts(now, limit); }
    @Override
    public void setAttemptState(String id, UploadAttemptState state, LocalDateTime expiresAt) { mapper.setAttemptState(id, state, expiresAt); }
}
