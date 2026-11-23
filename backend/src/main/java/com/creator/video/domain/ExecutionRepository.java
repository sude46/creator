package com.creator.video.domain;

import java.time.LocalDateTime;
import java.util.List;

public interface ExecutionRepository {
    record Video(long id, long ownerId, long processingGeneration, Long sourceAssetId, String processingStatus,
            boolean moderationBlocked, LocalDateTime deletedAt) { }
    record Task(long id, long videoId, long generation, String state, String executionToken,
            LocalDateTime executionLeaseUntil, int executionAttempts, LocalDateTime nextAttemptAt) { }
    record Source(String objectKey, long sizeBytes) { }
    record Output(String executionToken, long taskId, String objectKey, String state, LocalDateTime expiresAt) { }
    Video lockVideo(long id);
    Task lockTask(long id);
    boolean activeOwner(long id);
    Source source(long assetId, long videoId);
    void start(ExecutionClaim claim, LocalDateTime leaseUntil);
    void createOutput(ExecutionClaim claim, LocalDateTime expiresAt);
    void videoState(long videoId, String state);
    void finishTask(long taskId, String state, String failure, LocalDateTime retryAt);
    void bind(ExecutionClaim claim, MediaMetadata metadata, long coverBytes, List<HlsVariant> hls);
    List<ProcessingMessage> recoverable(LocalDateTime now, int limit);
    List<Output> expiredOutputs(LocalDateTime now, int limit);
    Output lockOutput(String token);
    boolean referenced(String key);
    void outputState(String token, String state, LocalDateTime expiresAt);
}
