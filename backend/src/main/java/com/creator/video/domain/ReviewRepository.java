package com.creator.video.domain;

import com.creator.common.PageRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository {
    boolean updateMetadata(long ownerId, long videoId, long version, VideoMetadata metadata);
    boolean submit(long ownerId, long videoId, long version, LocalDateTime now);
    boolean withdraw(long ownerId, long videoId, long version, long round);
    Optional<CreatorVideo> pending(long videoId, long reviewerId, boolean lock);
    List<CreatorVideo> queue(long reviewerId, PageRequest page);
    long queueCount(long reviewerId);
    boolean decide(long videoId, long reviewerId, long version, long round, ReviewDecision decision, LocalDateTime now);
    void record(CreatorVideo snapshot, long reviewerId, ReviewDecision decision);
    boolean hasHistory(long videoId);
    List<ReviewRecord> history(long videoId, PageRequest page);
    long historyCount(long videoId);
}
