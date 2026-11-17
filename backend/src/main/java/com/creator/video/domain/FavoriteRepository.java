package com.creator.video.domain;

import com.creator.common.PageRequest;
import java.util.List;

public interface FavoriteRepository {
    void add(long userId, long videoId);
    void remove(long userId, long videoId);
    boolean contains(long userId, long videoId);
    List<Favorite> findPage(long userId, PageRequest page);
    long count(long userId);
}
