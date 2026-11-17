package com.creator.video.domain;

import com.creator.common.PageRequest;
import java.util.List;
import java.util.Optional;

public interface CreatorVideoRepository {
    Optional<CreatorVideo> findOwned(long ownerId, long videoId, boolean lock);
    List<CreatorVideo> findPage(long ownerId, PageRequest page);
    long count(long ownerId);
    boolean retry(long ownerId, long videoId, long version, long generation);
    void insertTask(long videoId, long generation);
}
