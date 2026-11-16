package com.creator.video.domain;

import com.creator.common.PageRequest;
import java.util.List;
import java.util.Optional;

public interface DraftRepository {
    Optional<DraftCreation> findCreation(long ownerId, String requestKey, boolean lock);
    Optional<VideoDraft> findOwned(long ownerId, long id);
    List<VideoDraft> findPage(long ownerId, PageRequest page);
    long count(long ownerId);
    boolean insert(long ownerId, String requestKey, VideoMetadata metadata);
    boolean update(long ownerId, long id, long version, VideoMetadata metadata);
}
