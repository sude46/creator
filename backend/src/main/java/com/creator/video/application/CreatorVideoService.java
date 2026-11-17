package com.creator.video.application;

import com.creator.audit.application.AuditLog;
import com.creator.common.*;
import com.creator.identity.domain.Actor;
import com.creator.video.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreatorVideoService {
    private final CreatorVideoRepository videos;
    private final UploadRepository uploads;
    private final AuditLog audit;
    private final int maxPending;

    public CreatorVideoService(CreatorVideoRepository videos, UploadRepository uploads, AuditLog audit,
            @Value("${creator.upload.max-pending:5}") int maxPending) {
        if (maxPending < 1 || maxPending > 20) throw new IllegalArgumentException("Pending video limit must be 1..20");
        this.videos = videos;
        this.uploads = uploads;
        this.audit = audit;
        this.maxPending = maxPending;
    }

    @Transactional(readOnly = true)
    public CreatorVideo get(Actor actor, long id) { return owned(actor, id, false); }

    @Transactional(readOnly = true)
    public PageResult<CreatorVideo> list(Actor actor, PageRequest page) {
        return new PageResult<>(videos.findPage(actor.id(), page), videos.count(actor.id()), page.page(), page.size());
    }

    @Transactional(timeout = 5)
    public CreatorVideo retry(Actor actor, long id, long version) {
        if (!uploads.lockActiveOwner(actor)) throw new BusinessException(ErrorCode.SESSION_INVALID);
        var video = owned(actor, id, true);
        video.requireVersion(version);
        video.requireRetryAllowed();
        if (uploads.pendingCount(actor.id()) >= maxPending) throw new BusinessException(ErrorCode.UPLOAD_QUOTA_EXCEEDED);
        if (!videos.retry(actor.id(), id, version, video.processingGeneration())) throw new BusinessException(ErrorCode.STALE_WRITE);
        videos.insertTask(id, video.processingGeneration() + 1);
        audit.record(actor, "PROCESSING_RETRIED", "VIDEO", id);
        return owned(actor, id, false);
    }

    private CreatorVideo owned(Actor actor, long id, boolean lock) {
        return videos.findOwned(actor.id(), id, lock).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
