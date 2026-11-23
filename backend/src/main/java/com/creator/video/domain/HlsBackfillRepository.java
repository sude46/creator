package com.creator.video.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface HlsBackfillRepository {
    record Task(long id, long videoId, long generation, String sourceKey, long sourceBytes, int attempts, String token) { }
    record StateCount(String state, long count) { }
    List<Long> eligible(int limit);
    List<Long> eligibleVideo(long videoId);
    String state(long videoId);
    void insert(long videoId);
    void resetFailed(long videoId);
    Optional<Task> lockNext(LocalDateTime now);
    void markRunning(long id, String token, LocalDateTime leaseUntil);
    Optional<Long> lockOwned(long id, String token, LocalDateTime now);
    boolean ready(long videoId);
    boolean hasHls(long videoId);
    void insertHls(long videoId, HlsVariant variant);
    void done(long id, String token);
    void retry(long id, String token, LocalDateTime nextAttemptAt, String error);
    void fail(long id, String token, String error);
    List<StateCount> counts();
}
