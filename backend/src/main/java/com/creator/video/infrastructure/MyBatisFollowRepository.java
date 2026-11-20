package com.creator.video.infrastructure;

import com.creator.common.PageRequest;
import com.creator.video.domain.AuthorCard;
import com.creator.video.domain.FollowRepository;
import java.util.List;
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
    public List<AuthorCard> followers(long userId, long viewerId, PageRequest page) {
        return mapper.followers(userId, viewerId, page.size(), page.offset());
    }
    public List<AuthorCard> following(long userId, long viewerId, PageRequest page) {
        return mapper.following(userId, viewerId, page.size(), page.offset());
    }
}
