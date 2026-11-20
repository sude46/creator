package com.creator.video.domain;

import com.creator.common.PageRequest;
import java.util.List;

public interface ViewHistoryRepository {
    void record(long userId, long videoId);
    List<ViewHistory> findPage(long userId, PageRequest page);
    long count(long userId);
    void remove(long userId, long videoId);
    void clear(long userId);
}
