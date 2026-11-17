package com.creator.video.domain;

import com.creator.common.PageRequest;
import java.util.List;
import java.util.Optional;

public interface VideoQueryRepository {
    List<PublicVideo> findPage(VideoSearch search, PageRequest page);
    long count(VideoSearch search);
    Optional<PublicVideo> findPublic(long videoId);
    Optional<MediaTarget> media(long videoId, long actorId, MediaPurpose purpose, MediaKind kind, Long version, Long round);
}
