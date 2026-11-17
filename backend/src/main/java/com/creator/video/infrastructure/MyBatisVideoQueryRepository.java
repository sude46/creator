package com.creator.video.infrastructure;

import com.creator.common.PageRequest;
import com.creator.video.domain.*;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisVideoQueryRepository implements VideoQueryRepository {
    private final VideoQueryMapper mapper;
    public MyBatisVideoQueryRepository(VideoQueryMapper mapper) { this.mapper = mapper; }
    public List<PublicVideo> findPage(VideoSearch search, PageRequest page) {
        return mapper.findPage(search.title().isEmpty() ? null : search.title(), search.pattern(), search.categoryId(), page.size(), page.offset());
    }
    public long count(VideoSearch search) { return mapper.count(search.title().isEmpty() ? null : search.title(), search.pattern(), search.categoryId()); }
    public Optional<PublicVideo> findPublic(long videoId) { return Optional.ofNullable(mapper.findPublic(videoId)); }
    public Optional<MediaTarget> media(long videoId, long actorId, MediaPurpose purpose, MediaKind kind, Long version, Long round) {
        return Optional.ofNullable(mapper.media(videoId, actorId, purpose, kind, version, round));
    }
}
