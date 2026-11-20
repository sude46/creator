package com.creator.video.domain;

import com.creator.common.PageRequest;
import java.util.List;

public interface FollowRepository {
    boolean exists(long followerId, long followeeId);
    void add(long followerId, long followeeId);
    boolean remove(long followerId, long followeeId);
    long followerCount(long userId);
    long followingCount(long userId);
    String activeUsername(long userId);
    List<AuthorCard> followers(long userId, long viewerId, PageRequest page);
    List<AuthorCard> following(long userId, long viewerId, PageRequest page);
}
