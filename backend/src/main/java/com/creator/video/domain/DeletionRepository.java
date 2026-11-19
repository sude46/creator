package com.creator.video.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DeletionRepository {
    void create(long videoId, long ownerId, String reason);
    List<VideoDeletion> due(LocalDateTime now, int limit);
    boolean claim(long id, LocalDateTime now, LocalDateTime leaseUntil);
    List<String> objectKeys(long videoId);
    void finish(long id, long videoId, LocalDateTime now);
    void retry(long id, LocalDateTime nextAttemptAt, String error);
}
