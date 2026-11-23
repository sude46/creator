package com.creator.video.infrastructure;

import com.creator.video.domain.HlsBackfillRepository;
import com.creator.video.domain.HlsVariant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisHlsBackfillRepository implements HlsBackfillRepository {
    private final HlsBackfillMapper mapper;
    public MyBatisHlsBackfillRepository(HlsBackfillMapper mapper) { this.mapper = mapper; }
    @Override public List<Long> eligible(int limit) { return mapper.eligible(limit); }
    @Override public List<Long> eligibleVideo(long videoId) { return mapper.eligibleVideo(videoId); }
    @Override public String state(long videoId) { return mapper.state(videoId); }
    @Override public void insert(long videoId) { mapper.insert(videoId); }
    @Override public void resetFailed(long videoId) { mapper.resetFailed(videoId); }
    @Override public Optional<Task> lockNext(LocalDateTime now) { return Optional.ofNullable(mapper.lockNext(now)); }
    @Override public void markRunning(long id, String token, LocalDateTime leaseUntil) {
        mapper.markRunning(id, token, leaseUntil);
    }
    @Override public Optional<Long> lockOwned(long id, String token, LocalDateTime now) {
        return Optional.ofNullable(mapper.lockOwned(id, token, now));
    }
    @Override public boolean ready(long videoId) { return mapper.lockReady(videoId) != null; }
    @Override public boolean hasHls(long videoId) { return mapper.hlsCount(videoId) > 0; }
    @Override public void insertHls(long videoId, HlsVariant variant) {
        mapper.insertHls(videoId, variant);
        for (String key : variant.segmentKeys()) mapper.insertHlsSegment(videoId, key);
    }
    @Override public void done(long id, String token) { mapper.done(id, token); }
    @Override public void retry(long id, String token, LocalDateTime nextAttemptAt, String error) {
        mapper.retry(id, token, nextAttemptAt, error);
    }
    @Override public void fail(long id, String token, String error) { mapper.fail(id, token, error); }
    @Override public List<StateCount> counts() { return mapper.counts(); }
}
