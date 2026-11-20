package com.creator.video.domain;

import com.creator.common.PageRequest;
import java.util.List;
import java.util.Optional;

public interface VideoQueryRepository {
    List<PublicVideo> findPage(VideoSearch search, PageRequest page);
    List<PublicVideo> findPopularPage(VideoSearch search, PageRequest page);
    List<PublicVideo> findRecommendedPage(VideoSearch search, PageRequest page);
    long count(VideoSearch search);
    Optional<PublicVideo> findPublic(long videoId);
    boolean incrementView(long videoId);
    Optional<PublicVideo> findPublicInSearch(VideoSearch search, long videoId);
    AnchoredVideos anchor(VideoSearch search, PublicVideo anchor, int before, int after);
    List<PublicVideo> findPublicByIds(List<Long> videoIds);
    List<PublicVideo> findPageByAuthor(long authorId, PageRequest page);
    long countByAuthor(long authorId);
    List<PublicVideo> findFollowingPage(long followerId, PageRequest page);
    long countFollowing(long followerId);
    Optional<MediaTarget> media(long videoId, long actorId, MediaPurpose purpose, MediaKind kind, Long version, Long round);
}
