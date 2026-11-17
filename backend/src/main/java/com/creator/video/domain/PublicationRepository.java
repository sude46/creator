package com.creator.video.domain;

import com.creator.common.PageRequest;
import java.time.LocalDateTime;
import java.util.*;

public interface PublicationRepository {
    Optional<CreatorVideo> find(long videoId, boolean lock);
    List<CreatorVideo> managed(PageRequest page);
    long managedCount();
    boolean offline(long ownerId, long videoId, long version);
    boolean publish(long ownerId, long videoId, long version, LocalDateTime now);
    boolean block(long videoId, long version);
    boolean unblock(long videoId, long version);
    List<ModerationRecord> history(long videoId, PageRequest page);
    long historyCount(long videoId);
}
