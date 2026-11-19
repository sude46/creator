package com.creator.video.infrastructure;

import com.creator.video.domain.FollowRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisFollowRepository implements FollowRepository {
    private final FollowMapper mapper;
    public MyBatisFollowRepository(FollowMapper mapper) { this.mapper = mapper; }
    public boolean exists(long followerId, long followeeId) { return mapper.exists(followerId, followeeId); }
    public void add(long followerId, long followeeId) {
        try { mapper.add(followerId, followeeId); }
        catch (DuplicateKeyException ignored) { }
    }
    public boolean remove(long followerId, long followeeId) { return mapper.remove(followerId, followeeId) == 1; }
    public long followerCount(long userId) { return mapper.followerCount(userId); }
    public long followingCount(long userId) { return mapper.followingCount(userId); }
    public String activeUsername(long userId) { return mapper.activeUsername(userId); }
}
