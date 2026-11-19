package com.creator.video.domain;

public interface LikeRepository {
    boolean exists(long userId, long videoId);
    void add(long userId, long videoId);
    boolean remove(long userId, long videoId);
}
