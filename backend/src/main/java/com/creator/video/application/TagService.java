package com.creator.video.application;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.identity.domain.Actor;
import com.creator.video.domain.TagCount;
import com.creator.video.domain.TagRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TagService {
    private final TagRepository tags;
    private final PublicVideoService publicVideos;
    private final CreatorVideoService creatorVideos;
    public TagService(TagRepository tags, PublicVideoService publicVideos, CreatorVideoService creatorVideos) {
        this.tags = tags;
        this.publicVideos = publicVideos;
        this.creatorVideos = creatorVideos;
    }

    @Transactional(readOnly = true)
    public List<String> publicTags(long videoId) {
        publicVideos.get(videoId);
        return tags.tags(videoId);
    }

    @Transactional(readOnly = true)
    public List<String> ownerTags(Actor actor, long videoId) {
        creatorVideos.get(actor, videoId);
        return tags.tags(videoId);
    }

    @Transactional(readOnly = true)
    public List<TagCount> popular(int limit) {
        if (limit < 1 || limit > 50) throw new BusinessException(ErrorCode.INVALID_PAGE);
        return tags.popular(limit);
    }
}
