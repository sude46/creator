package com.creator.video.domain;

import com.creator.identity.domain.Actor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UploadRepository {
    Optional<UploadVideo> findOwned(long ownerId, long videoId, boolean lock);
    boolean lockActiveOwner(Actor actor);
    long usedBytes(long ownerId);
    long pendingCount(long ownerId);
    long activeAttempts(long ownerId);
    void insertAttempt(UploadAttempt attempt);
    Optional<UploadAttempt> findAttempt(String id);
    Optional<UploadAttempt> findActiveAttempt(long ownerId, long videoId, LocalDateTime now);
    Optional<UploadAttempt> lockAttempt(String id);
    List<UploadChunk> chunks(String attemptId);
    void insertChunk(String attemptId, int index, int size);
    void deleteChunks(String attemptId);
    void bind(UploadAttempt attempt, long size, long version);
    boolean hasReference(String objectKey);
    List<String> expiredAttempts(LocalDateTime now, int limit);
    void setAttemptState(String id, UploadAttemptState state, LocalDateTime expiresAt);
}
