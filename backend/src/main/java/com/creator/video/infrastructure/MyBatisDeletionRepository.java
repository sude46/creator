package com.creator.video.infrastructure;

import com.creator.video.domain.DeletionRepository;
import com.creator.video.domain.VideoDeletion;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisDeletionRepository implements DeletionRepository {
    private final VideoDeletionMapper mapper;

    public MyBatisDeletionRepository(VideoDeletionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void create(long videoId, long ownerId, String reason) {
        mapper.insert(videoId, ownerId, reason);
    }

    @Override
    public List<VideoDeletion> due(LocalDateTime now, int limit) {
        return mapper.due(now, limit);
    }

    @Override
    public boolean claim(long id, LocalDateTime now, LocalDateTime leaseUntil) {
        return mapper.claim(id, now, leaseUntil) == 1;
    }

    @Override
    public List<String> objectKeys(long videoId) {
        return mapper.objectKeys(videoId);
    }

    @Override
    public void finish(long id, long videoId, LocalDateTime now) {
        mapper.done(id);
        mapper.clearSource(videoId);
        mapper.deleteAssets(videoId);
        mapper.deleteHlsSegments(videoId);
        mapper.deleteHls(videoId);
        mapper.deleteHlsBackfill(videoId);
    }

    @Override
    public void retry(long id, LocalDateTime nextAttemptAt, String error) {
        mapper.retry(id, nextAttemptAt, error);
    }
}
