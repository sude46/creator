package com.creator.video.application;

import com.creator.audit.application.AuditLog;
import com.creator.catalog.application.CategorySelection;
import com.creator.common.*;
import com.creator.identity.domain.*;
import com.creator.video.domain.*;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {
    private final CreatorVideoRepository videos;
    private final ReviewRepository reviews;
    private final UploadRepository accounts;
    private final CategorySelection categories;
    private final AuditLog audit;
    private final Clock clock;
    private final ObjectProvider<NotificationService> notifications;
    private final TagRepository tags;

    public ReviewService(CreatorVideoRepository videos, ReviewRepository reviews, UploadRepository accounts,
            CategorySelection categories, AuditLog audit, Clock clock, ObjectProvider<NotificationService> notifications,
            TagRepository tags) {
        this.videos = videos;
        this.reviews = reviews;
        this.accounts = accounts;
        this.categories = categories;
        this.audit = audit;
        this.clock = clock;
        this.notifications = notifications;
        this.tags = tags;
    }

    @Transactional(timeout = 5)
    public CreatorVideo update(Actor actor, long id, long version, VideoMetadata metadata) {
        active(actor);
        var initial = owned(actor, id, false);
        if (initial.categoryId() != metadata.categoryId()) categories.requireEnabled(metadata.categoryId());
        var video = owned(actor, id, true);
        video.requireVersion(version);
        video.requireEditable();
        if (!reviews.updateMetadata(actor.id(), id, version, metadata)) stale();
        tags.replace(id, metadata.tags());
        audit.record(actor, "VIDEO_METADATA_UPDATED", "VIDEO", id);
        return owned(actor, id, false);
    }

    @Transactional(timeout = 5)
    public CreatorVideo submit(Actor actor, long id, long version) {
        active(actor);
        var initial = owned(actor, id, false);
        categories.requireEnabled(initial.categoryId());
        var video = owned(actor, id, true);
        video.requireVersion(version);
        video.requireSubmittable();
        new VideoMetadata(video.title(), video.description(), video.categoryId());
        if (!reviews.submit(actor.id(), id, version, now())) stale();
        audit.record(actor, "REVIEW_SUBMITTED", "VIDEO", id);
        return owned(actor, id, false);
    }

    @Transactional(timeout = 5)
    public CreatorVideo withdraw(Actor actor, long id, long version) {
        active(actor);
        var video = owned(actor, id, true);
        video.requireVersion(version);
        video.requirePending(video.reviewRound());
        if (!reviews.withdraw(actor.id(), id, version, video.reviewRound())) stale();
        audit.record(actor, "REVIEW_WITHDRAWN", "VIDEO", id);
        return owned(actor, id, false);
    }

    @Transactional(readOnly = true)
    public PageResult<CreatorVideo> queue(Actor actor, PageRequest page) {
        requireAdmin(actor);
        return new PageResult<>(reviews.queue(actor.id(), page), reviews.queueCount(actor.id()), page.page(), page.size());
    }

    @Transactional(readOnly = true)
    public CreatorVideo pending(Actor actor, long id) {
        requireAdmin(actor);
        return pending(actor, id, false);
    }

    @Transactional(timeout = 5)
    public CreatorVideo decide(Actor actor, long id, long version, long round, ReviewDecision decision) {
        requireAdmin(actor);
        active(actor);
        var initial = decisionTarget(actor, id, false);
        if (decision.outcome() == ReviewDecision.Outcome.APPROVED) categories.requireEnabled(initial.categoryId());
        else categories.requireExisting(initial.categoryId());
        var video = decisionTarget(actor, id, true);
        video.requireVersion(version);
        video.requirePending(round);
        if (!reviews.decide(id, actor.id(), version, round, decision, now())) stale();
        reviews.record(video, actor.id(), decision);
        audit.record(actor, "REVIEW_" + decision.outcome().name(), "VIDEO", id);
        notifications.ifAvailable(service -> service.review(video.ownerId(),
                decision.outcome() == ReviewDecision.Outcome.APPROVED, decision.reason(), id));
        return videos.findOwned(video.ownerId(), id, false).orElseThrow();
    }

    @Transactional(readOnly = true)
    public PageResult<ReviewRecord> ownerHistory(Actor actor, long id, PageRequest page) {
        owned(actor, id, false);
        return history(id, page);
    }

    @Transactional(readOnly = true)
    public PageResult<ReviewRecord> adminHistory(Actor actor, long id, PageRequest page) {
        requireAdmin(actor);
        if (!reviews.hasHistory(id)) throw new BusinessException(ErrorCode.NOT_FOUND);
        return history(id, page);
    }

    private PageResult<ReviewRecord> history(long id, PageRequest page) {
        return new PageResult<>(reviews.history(id, page), reviews.historyCount(id), page.page(), page.size());
    }
    private CreatorVideo owned(Actor actor, long id, boolean lock) {
        return videos.findOwned(actor.id(), id, lock).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
    private CreatorVideo pending(Actor actor, long id, boolean lock) {
        return reviews.pending(id, actor.id(), lock).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
    private CreatorVideo decisionTarget(Actor actor, long id, boolean lock) {
        return reviews.pending(id, actor.id(), lock).orElseThrow(() -> new BusinessException(
                reviews.hasHistory(id) ? ErrorCode.REVIEW_STATE_CONFLICT : ErrorCode.NOT_FOUND));
    }
    private void active(Actor actor) {
        if (!accounts.lockActiveOwner(actor)) throw new BusinessException(ErrorCode.SESSION_INVALID);
    }
    private void requireAdmin(Actor actor) {
        if (actor.role() != Role.ADMIN) throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    private void stale() { throw new BusinessException(ErrorCode.STALE_WRITE); }
    private LocalDateTime now() { return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS); }
}
