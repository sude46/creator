package com.creator.video.infrastructure;

import com.creator.common.PageRequest;
import com.creator.video.domain.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisReviewRepository implements ReviewRepository {
    private final ReviewMapper mapper;
    public MyBatisReviewRepository(ReviewMapper mapper) { this.mapper = mapper; }
    public boolean updateMetadata(long ownerId, long videoId, long version, VideoMetadata metadata) { return mapper.updateMetadata(ownerId, videoId, version, metadata) == 1; }
    public boolean submit(long ownerId, long videoId, long version, LocalDateTime now) { return mapper.submit(ownerId, videoId, version, now) == 1; }
    public boolean withdraw(long ownerId, long videoId, long version, long round) { return mapper.withdraw(ownerId, videoId, version, round) == 1; }
    public Optional<CreatorVideo> pending(long videoId, long reviewerId, boolean lock) {
        return Optional.ofNullable(lock ? mapper.lockPending(videoId, reviewerId) : mapper.pending(videoId, reviewerId));
    }
    public List<CreatorVideo> queue(long reviewerId, PageRequest page) { return mapper.queue(reviewerId, page.size(), page.offset()); }
    public long queueCount(long reviewerId) { return mapper.queueCount(reviewerId); }
    public boolean decide(long videoId, long reviewerId, long version, long round, ReviewDecision decision, LocalDateTime now) {
        boolean approved = decision.outcome() == ReviewDecision.Outcome.APPROVED;
        return mapper.decide(videoId, reviewerId, version, round, decision, approved ? "PUBLISHED" : "UNPUBLISHED", approved ? now : null) == 1;
    }
    public void record(CreatorVideo snapshot, long reviewerId, ReviewDecision decision) { mapper.record(snapshot, reviewerId, decision); }
    public boolean hasHistory(long videoId) { return mapper.hasHistory(videoId); }
    public List<ReviewRecord> history(long videoId, PageRequest page) { return mapper.history(videoId, page.size(), page.offset()); }
    public long historyCount(long videoId) { return mapper.historyCount(videoId); }
}
