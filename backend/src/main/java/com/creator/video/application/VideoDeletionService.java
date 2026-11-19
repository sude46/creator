package com.creator.video.application;

import com.creator.audit.application.AuditLog;
import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.identity.domain.Actor;
import com.creator.identity.domain.Role;
import com.creator.video.domain.CreatorVideo;
import com.creator.video.domain.CreatorVideoRepository;
import com.creator.video.domain.DeletionRepository;
import com.creator.video.domain.ModerationReason;
import com.creator.video.domain.UploadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VideoDeletionService {
    private final CreatorVideoRepository videos;
    private final DeletionRepository deletions;
    private final UploadRepository accounts;
    private final AuditLog audit;

    public VideoDeletionService(CreatorVideoRepository videos, DeletionRepository deletions,
            UploadRepository accounts, AuditLog audit) {
        this.videos = videos;
        this.deletions = deletions;
        this.accounts = accounts;
        this.audit = audit;
    }

    @Transactional(timeout = 5)
    public void deleteByOwner(Actor actor, long videoId, long version) {
        active(actor);
        var video = videos.findAny(videoId, true).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (video.ownerId() != actor.id()) throw new BusinessException(ErrorCode.NOT_FOUND);
        video.requireVersion(version);
        remove(actor, video, null);
    }

    @Transactional(timeout = 5)
    public void deleteByAdmin(Actor actor, long videoId, long version, ModerationReason reason) {
        if (actor == null || actor.role() != Role.ADMIN) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (reason == null) throw new BusinessException(ErrorCode.INVALID_MODERATION_REASON);
        active(actor);
        var video = videos.findAny(videoId, true).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        video.requireVersion(version);
        remove(actor, video, reason.value());
    }

    private void remove(Actor actor, CreatorVideo video, String reason) {
        Long ownerFilter = actor.role() == Role.ADMIN ? null : actor.id();
        if (!videos.softDelete(video.id(), ownerFilter, video.version())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        videos.cancelActiveTasks(video.id());
        deletions.create(video.id(), video.ownerId(), reason);
        audit.record(actor, "VIDEO_DELETED", "VIDEO", video.id(), reason);
    }

    private void active(Actor actor) {
        if (actor == null) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        if (!accounts.lockActiveOwner(actor)) throw new BusinessException(ErrorCode.SESSION_INVALID);
    }
}
