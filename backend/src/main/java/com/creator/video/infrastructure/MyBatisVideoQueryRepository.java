package com.creator.video.infrastructure;

import com.creator.common.PageRequest;
import com.creator.video.domain.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisVideoQueryRepository implements VideoQueryRepository {
    private final VideoQueryMapper mapper;
    public MyBatisVideoQueryRepository(VideoQueryMapper mapper) { this.mapper = mapper; }
    public List<PublicVideo> findPage(VideoSearch search, PageRequest page) {
        return mapper.findPage(search.title(), search.pattern(), search.categoryId(), search.tag(), page.size(), page.offset());
    }
    public long count(VideoSearch search) { return mapper.count(search.title(), search.pattern(), search.categoryId(), search.tag()); }
    public List<PublicVideo> findPopularPage(VideoSearch search, PageRequest page) {
        return mapper.findPopularPage(search.title(), search.pattern(), search.categoryId(), search.tag(), page.size(), page.offset());
    }
    public Optional<PublicVideo> findPublic(long videoId) { return Optional.ofNullable(mapper.findPublic(videoId)); }
    public boolean incrementView(long videoId) { return mapper.incrementView(videoId) == 1; }
    public Optional<PublicVideo> findPublicInSearch(VideoSearch search, long videoId) {
        return Optional.ofNullable(mapper.findPublicInSearch(videoId, search.title(), search.pattern(), search.categoryId(), search.tag()));
    }
    public List<PublicVideo> findPublicByIds(List<Long> videoIds) {
        if (videoIds.isEmpty()) return List.of();
        return mapper.findPublicByIds(videoIds);
    }
    public List<PublicVideo> findPageByAuthor(long authorId, PageRequest page) {
        return mapper.findPageByAuthor(authorId, page.size(), page.offset());
    }
    public long countByAuthor(long authorId) { return mapper.countByAuthor(authorId); }
    public List<PublicVideo> findFollowingPage(long followerId, PageRequest page) {
        return mapper.findFollowingPage(followerId, page.size(), page.offset());
    }
    public long countFollowing(long followerId) { return mapper.countFollowing(followerId); }
    public Optional<MediaTarget> media(long videoId, long actorId, MediaPurpose purpose, MediaKind kind, Long version, Long round) {
        return Optional.ofNullable(mapper.media(videoId, actorId, purpose, kind, version, round));
    }
    public AnchoredVideos anchor(VideoSearch search, PublicVideo anchor, int before, int after) {
        var newer = mapper.findNewer(search.title(), search.pattern(), search.categoryId(), search.tag(), anchor.publishedAt(), anchor.id(), before + 1);
        var older = mapper.findOlder(search.title(), search.pattern(), search.categoryId(), search.tag(), anchor.publishedAt(), anchor.id(), after + 1);
        boolean hasBefore = newer.size() > before;
        boolean hasAfter = older.size() > after;
        int beforeCount = Math.min(before, newer.size());
        var items = new ArrayList<PublicVideo>();
        items.addAll(newer.subList(newer.size() - beforeCount, newer.size()));
        items.add(anchor);
        items.addAll(older.subList(0, Math.min(after, older.size())));
        return new AnchoredVideos(items, beforeCount, hasBefore, hasAfter);
    }
}
