package com.creator.video.application;

import com.creator.audit.application.AuditLog;
import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.identity.domain.Actor;
import com.creator.identity.domain.Role;
import com.creator.video.domain.CreatorVideo;
import com.creator.video.domain.CreatorVideoRepository;
import com.creator.video.domain.ModerationReason;
import com.creator.video.domain.UploadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VideoProcessingService {
    private final CreatorVideoRepository videos;
    private final UploadRepository accounts;
    private final AuditLog audit;

    public VideoProcessingService(CreatorVideoRepository videos, UploadRepository accounts, AuditLog audit) {
        this.videos = videos;
        this.accounts = accounts;
        this.audit = audit;
    }

    @Transactional(timeout = 5)
    public CreatorVideo cancelByOwner(Actor actor, long videoId, long version) {
        active(actor);
        var video = videos.findOwned(actor.id(), videoId, true)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        cancel(actor, video, version, null);
        return videos.findOwned(actor.id(), videoId, false).orElseThrow();
    }

    @Transactional(timeout = 5)
    public CreatorVideo cancelByAdmin(Actor actor, long videoId, long version, ModerationReason reason) {
        if (actor == null || actor.role() != Role.ADMIN) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (reason == null) throw new BusinessException(ErrorCode.INVALID_MODERATION_REASON);
        active(actor);
        var video = videos.findAny(videoId, true).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        cancel(actor, video, version, reason.value());
        return videos.findAny(videoId, false).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private void cancel(Actor actor, CreatorVideo video, long version, String reason) {
        video.requireVersion(version);
        if (!(video.processingStatus().equals("PENDING") || video.processingStatus().equals("PROCESSING"))) {
            throw new BusinessException(ErrorCode.PUBLICATION_STATE_CONFLICT);
        }
        Long ownerFilter = actor.role() == Role.ADMIN ? null : actor.id();
        if (!videos.markProcessingCancelled(video.id(), ownerFilter, version)) {
            throw new BusinessException(ErrorCode.STALE_WRITE);
        }
        videos.cancelActiveTasks(video.id());
        audit.record(actor, "VIDEO_PROCESSING_CANCELLED", "VIDEO", video.id(), reason);
    }

    private void active(Actor actor) {
        if (actor == null) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        if (!accounts.lockActiveOwner(actor)) throw new BusinessException(ErrorCode.SESSION_INVALID);
    }
}
