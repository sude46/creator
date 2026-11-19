package com.creator.video.infrastructure;

import com.creator.video.domain.LikeRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisLikeRepository implements LikeRepository {
    private final LikeMapper mapper;
    public MyBatisLikeRepository(LikeMapper mapper) { this.mapper = mapper; }
    public boolean exists(long userId, long videoId) { return mapper.exists(userId, videoId); }
    public void add(long userId, long videoId) {
        try { mapper.add(userId, videoId); }
        catch (DuplicateKeyException ignored) { /* idempotent */ }
    }
    public boolean remove(long userId, long videoId) { return mapper.remove(userId, videoId) == 1; }
}
