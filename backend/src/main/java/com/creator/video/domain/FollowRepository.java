package com.creator.video.domain;

public interface FollowRepository {
    boolean exists(long followerId, long followeeId);
    void add(long followerId, long followeeId);
    boolean remove(long followerId, long followeeId);
    long followerCount(long userId);
    long followingCount(long userId);
    String activeUsername(long userId);
}
