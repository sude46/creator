package com.creator.video.application;

import com.creator.audit.application.AuditLog;
import com.creator.common.*;
import com.creator.identity.application.AccountQueryService;
import com.creator.identity.domain.*;
import com.creator.video.domain.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicationService {
    private final PublicationRepository publications;
    private final CreatorVideoRepository videos;
    private final UploadRepository accounts;
    private final AccountQueryService accountQueries;
    private final AuditLog audit;
    private final Clock clock;

    public PublicationService(PublicationRepository publications, CreatorVideoRepository videos, UploadRepository accounts,
            AccountQueryService accountQueries, AuditLog audit, Clock clock) {
        this.publications = publications;
        this.videos = videos;
        this.accounts = accounts;
        this.accountQueries = accountQueries;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(timeout = 5)
    public CreatorVideo offline(Actor actor, long id, long version) {
        active(actor);
        var video = owned(actor, id, true);
        video.requireVersion(version);
        video.requireOffline();
        changed(publications.offline(actor.id(), id, version));
        audit.record(actor, "VIDEO_OFFLINE", "VIDEO", id);
        return owned(actor, id, false);
    }

    @Transactional(timeout = 5)
    public CreatorVideo publish(Actor actor, long id, long version) {
        active(actor);
        var video = owned(actor, id, true);
        video.requireVersion(version);
        video.requirePublish();
        changed(publications.publish(actor.id(), id, version, LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS)));
        audit.record(actor, "VIDEO_REPUBLISHED", "VIDEO", id);
        return owned(actor, id, false);
    }

    @Transactional(timeout = 5)
    public CreatorVideo block(Actor actor, long id, long version, ModerationReason reason) {
        moderator(actor);
        requireReason(reason);
        active(actor);
        var video = target(id, true);
        video.requireVersion(version);
        video.requireModeration(false);
        changed(publications.block(id, version));
        audit.record(actor, "VIDEO_BLOCKED", "VIDEO", id, reason.value());
        return target(id, false);
    }

    @Transactional(timeout = 5)
    public CreatorVideo unblock(Actor actor, long id, long version, ModerationReason reason) {
        moderator(actor);
        requireReason(reason);
        active(actor);
        var video = target(id, true);
        video.requireVersion(version);
        video.requireModeration(true);
        changed(publications.unblock(id, version));
        audit.record(actor, "VIDEO_UNBLOCKED", "VIDEO", id, reason.value());
        return target(id, false);
    }

    @Transactional(readOnly = true)
    public PageResult<CreatorVideo> processing(Actor actor, PageRequest page) {
        currentAdmin(actor);
        return new PageResult<>(publications.processing(page), publications.processingCount(), page.page(), page.size());
    }

    @Transactional(readOnly = true)
    public PageResult<CreatorVideo> managed(Actor actor, PageRequest page) {
        currentAdmin(actor);
        return new PageResult<>(publications.managed(page), publications.managedCount(), page.page(), page.size());
    }

    @Transactional(readOnly = true)
    public CreatorVideo managed(Actor actor, long id) {
        currentAdmin(actor);
        var video = target(id, false);
        if (!video.processingStatus().equals("READY")) throw new BusinessException(ErrorCode.NOT_FOUND);
        return video;
    }

    @Transactional(readOnly = true)
    public PageResult<ModerationRecord> ownerHistory(Actor actor, long id, PageRequest page) {
        current(actor);
        owned(actor, id, false);
        return history(id, page);
    }

    @Transactional(readOnly = true)
    public PageResult<ModerationRecord> adminHistory(Actor actor, long id, PageRequest page) {
        managed(actor, id);
        return history(id, page);
    }

    private PageResult<ModerationRecord> history(long id, PageRequest page) {
        return new PageResult<>(publications.history(id, page), publications.historyCount(id), page.page(), page.size());
    }
    private CreatorVideo owned(Actor actor, long id, boolean lock) {
        return videos.findOwned(actor.id(), id, lock).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
    private CreatorVideo target(long id, boolean lock) {
        return publications.find(id, lock).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
    private void active(Actor actor) {
        if (actor == null) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        if (!accounts.lockActiveOwner(actor)) throw new BusinessException(ErrorCode.SESSION_INVALID);
    }
    private void current(Actor actor) {
        if (actor == null) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        if (!accountQueries.current(actor.username()).equals(actor)) throw new BusinessException(ErrorCode.SESSION_INVALID);
    }
    private void admin(Actor actor) {
        if (actor == null) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        if (!actor.adminArea()) throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    private void moderator(Actor actor) {
        admin(actor);
        if (!actor.canModerate()) throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    private void currentAdmin(Actor actor) { admin(actor); current(actor); }
    private void requireReason(ModerationReason reason) {
        if (reason == null) throw new BusinessException(ErrorCode.INVALID_MODERATION_REASON);
    }
    private void changed(boolean changed) { if (!changed) throw new BusinessException(ErrorCode.STALE_WRITE); }
}
