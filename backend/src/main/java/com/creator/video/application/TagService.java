package com.creator.video.application;

import com.creator.audit.application.AuditLog;
import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.identity.domain.Actor;
import com.creator.video.domain.TagAdmin;
import com.creator.video.domain.TagCount;
import com.creator.video.domain.TagRepository;
import com.creator.video.domain.VideoTags;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TagService {
    private final TagRepository tags;
    private final PublicVideoService publicVideos;
    private final CreatorVideoService creatorVideos;
    private final AuditLog audit;
    public TagService(TagRepository tags, PublicVideoService publicVideos, CreatorVideoService creatorVideos,
            AuditLog audit) {
        this.tags = tags;
        this.publicVideos = publicVideos;
        this.creatorVideos = creatorVideos;
        this.audit = audit;
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
    public List<TagAdmin> adminList(Actor actor) {
        requireSuper(actor);
        return tags.adminList();
    }

    @Transactional(timeout = 5)
    public TagAdmin rename(Actor actor, long id, String name) {
        requireSuper(actor);
        var tag = tags.find(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        String clean = VideoTags.normalizeName(name);
        try {
            if (!tags.rename(id, clean, VideoTags.key(clean))) throw new BusinessException(ErrorCode.STALE_WRITE);
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.TAG_NAME_TAKEN);
        }
        audit.record(actor, "TAG_RENAMED", "TAG", id, tag.name() + " → " + clean);
        return tags.find(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(timeout = 5)
    public void merge(Actor actor, long sourceId, long targetId) {
        requireSuper(actor);
        if (sourceId == targetId) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        tags.find(sourceId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        tags.find(targetId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        tags.merge(sourceId, targetId);
        audit.record(actor, "TAG_MERGED", "TAG", sourceId, "合并到 #" + targetId);
    }

    @Transactional(timeout = 5)
    public void delete(Actor actor, long id) {
        requireSuper(actor);
        var tag = tags.find(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (tag.uses() > 0) throw new BusinessException(ErrorCode.TAG_IN_USE);
        if (!tags.delete(id)) throw new BusinessException(ErrorCode.TAG_IN_USE);
        audit.record(actor, "TAG_DELETED", "TAG", id, tag.name());
    }

    private void requireSuper(Actor actor) {
        if (actor == null) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        if (!actor.superAdmin()) throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    @Transactional(readOnly = true)
    public List<TagCount> popular(int limit) {
        if (limit < 1 || limit > 50) throw new BusinessException(ErrorCode.INVALID_PAGE);
        return tags.popular(limit);
    }
}
