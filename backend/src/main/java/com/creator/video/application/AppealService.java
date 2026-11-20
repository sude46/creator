package com.creator.video.application;

import com.creator.audit.application.AuditLog;
import com.creator.common.*;
import com.creator.identity.application.AccountQueryService;
import com.creator.identity.domain.Actor;
import com.creator.video.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppealService {
    private final AppealRepository appeals;
    private final CreatorVideoRepository videos;
    private final PublicationService publications;
    private final AccountQueryService accounts;
    private final AuditLog audit;
    private final NotificationService notifications;

    public AppealService(AppealRepository appeals, CreatorVideoRepository videos, PublicationService publications,
            AccountQueryService accounts, AuditLog audit, NotificationService notifications) {
        this.appeals = appeals; this.videos = videos; this.publications = publications;
        this.accounts = accounts; this.audit = audit; this.notifications = notifications;
    }

    @Transactional(timeout = 5)
    public ContentAppeal create(Actor actor, String targetType, long targetId, String reason) {
        active(actor);
        String type = targetType == null ? "" : targetType.trim().toUpperCase();
        String clean = cleanReason(reason);
        if (!type.equals("VIDEO") || targetId <= 0) throw new BusinessException(ErrorCode.INVALID_APPEAL);
        var video = videos.findOwned(actor.id(), targetId, false).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!video.moderationBlocked()) throw new BusinessException(ErrorCode.INVALID_APPEAL);
        if (appeals.openExists(actor.id(), type, targetId)) throw new BusinessException(ErrorCode.APPEAL_ALREADY_OPEN);
        appeals.add(actor.id(), type, targetId, clean);
        audit.record(actor, "APPEAL_CREATED", "VIDEO", targetId, clean);
        return appeals.page(actor.id(), type, targetId, null, new PageRequest(1, 1)).getFirst();
    }

    @Transactional(readOnly = true)
    public PageResult<ContentAppeal> mine(Actor actor, String targetType, Long targetId, PageRequest page) {
        active(actor);
        String type = targetType == null || targetType.isBlank() ? null : targetType.trim().toUpperCase();
        if (type != null && !type.equals("VIDEO")) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return new PageResult<>(appeals.page(actor.id(), type, targetId, null, page),
                appeals.count(actor.id(), type, targetId, null), page.page(), page.size());
    }

    @Transactional(readOnly = true)
    public PageResult<ContentAppeal> adminList(Actor actor, String status, PageRequest page) {
        requireModerator(actor);
        String clean = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        if (clean != null && !(clean.equals("OPEN") || clean.equals("ACCEPTED") || clean.equals("REJECTED"))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return new PageResult<>(appeals.page(null, null, null, clean, page), appeals.count(null, null, null, clean),
                page.page(), page.size());
    }

    @Transactional(timeout = 5)
    public void resolve(Actor actor, long id, String action, String reason) {
        requireModerator(actor);
        active(actor);
        String cleanAction = action == null ? "" : action.trim().toUpperCase();
        if (!(cleanAction.equals("ACCEPT") || cleanAction.equals("REJECT"))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        String cleanReason = cleanReason(reason);
        var appeal = appeals.lock(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!appeal.status().equals("OPEN")) throw new BusinessException(ErrorCode.STALE_WRITE);
        if (cleanAction.equals("ACCEPT")) {
            var video = videos.findAny(appeal.targetId(), false)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
            if (!video.moderationBlocked()) throw new BusinessException(ErrorCode.PUBLICATION_STATE_CONFLICT);
            publications.unblock(actor, video.id(), video.version(), new ModerationReason("申诉受理：" + cleanReason));
        }
        boolean accepted = cleanAction.equals("ACCEPT");
        if (!appeals.resolve(id, actor.id(), accepted ? "ACCEPTED" : "REJECTED", cleanReason)) {
            throw new BusinessException(ErrorCode.STALE_WRITE);
        }
        audit.record(actor, "APPEAL_" + cleanAction, "VIDEO", appeal.targetId(), cleanReason);
        notifications.appealResolved(appeal.appellantId(), accepted, appeal.targetId(), cleanReason);
    }

    private String cleanReason(String reason) {
        String clean = reason == null ? "" : reason.strip();
        if (clean.isEmpty() || clean.length() > 500) throw new BusinessException(ErrorCode.INVALID_APPEAL);
        return clean;
    }

    private void active(Actor actor) {
        if (actor == null) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        if (!accounts.current(actor.username()).equals(actor)) throw new BusinessException(ErrorCode.SESSION_INVALID);
    }
    private void requireModerator(Actor actor) {
        active(actor);
        if (!actor.canModerate()) throw new BusinessException(ErrorCode.FORBIDDEN);
    }
}
