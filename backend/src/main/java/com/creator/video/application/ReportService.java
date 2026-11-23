package com.creator.video.application;

import com.creator.common.*;
import com.creator.identity.application.AccountQueryService;
import com.creator.identity.domain.Actor;
import com.creator.identity.domain.Role;
import com.creator.video.domain.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
    private final ReportRepository reports;
    private final VideoQueryRepository videos;
    private final CommentRepository comments;
    private final CreatorVideoRepository creatorVideos;
    private final VideoDeletionService deletions;
    private final CommentService commentService;
    private final AccountQueryService accounts;
    private final ObjectProvider<NotificationService> notifications;
    public ReportService(ReportRepository reports, VideoQueryRepository videos, CommentRepository comments,
            CreatorVideoRepository creatorVideos, VideoDeletionService deletions, CommentService commentService,
            AccountQueryService accounts, ObjectProvider<NotificationService> notifications) {
        this.reports = reports; this.videos = videos; this.comments = comments;
        this.creatorVideos = creatorVideos; this.deletions = deletions; this.commentService = commentService;
        this.accounts = accounts; this.notifications = notifications;
    }
    @Transactional(timeout = 5)
    public void report(Actor actor, String targetType, long targetId, String reason) {
        active(actor);
        String type = targetType == null ? "" : targetType.trim().toUpperCase();
        String clean = reason == null ? "" : reason.trim();
        if (!(type.equals("VIDEO") || type.equals("COMMENT")) || clean.isEmpty() || clean.length() > 500) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (type.equals("VIDEO")) videos.findPublic(targetId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        else comments.find(targetId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        reports.add(actor.id(), type, targetId, clean);
    }
    @Transactional(readOnly = true)
    public PageResult<ContentReport> list(Actor actor, String status, PageRequest page) {
        requireAdmin(actor);
        String clean = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        if (clean != null && !(clean.equals("OPEN") || clean.equals("DISMISSED") || clean.equals("REMOVED"))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return new PageResult<>(reports.page(clean, page), reports.count(clean), page.page(), page.size());
    }
    @Transactional(timeout = 5)
    public void resolve(Actor actor, long id, String action, String reason) {
        requireAdmin(actor);
        String cleanAction = action == null ? "" : action.trim().toUpperCase();
        if (!(cleanAction.equals("DISMISS") || cleanAction.equals("REMOVE"))) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        var report = reports.lock(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!report.status().equals("OPEN")) throw new BusinessException(ErrorCode.STALE_WRITE);
        String notificationLink = report.targetType().equals("VIDEO") ? "/videos/" + report.targetId()
                : comments.find(report.targetId()).map(comment -> "/videos/" + comment.videoId()).orElse(null);
        if (cleanAction.equals("REMOVE")) {
            if (report.targetType().equals("COMMENT")) commentService.delete(actor, report.targetId(), reason);
            else {
                var video = creatorVideos.findAny(report.targetId(), false).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
                deletions.deleteByAdmin(actor, video.id(), video.version(), new ModerationReason(reason == null ? "举报处理" : reason));
            }
        }
        boolean removed = cleanAction.equals("REMOVE");
        if (!reports.resolve(id, actor.id(), removed ? "REMOVED" : "DISMISSED", reason)) {
            throw new BusinessException(ErrorCode.STALE_WRITE);
        }
        String cleanReason = reason == null || reason.isBlank() ? "管理员已处理你的举报。" : reason;
        notifications.ifAvailable(service -> service.reportResolved(report.reporterId(), removed, cleanReason,
                removed ? null : notificationLink));
    }
    private void active(Actor actor) {
        if (actor == null) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        if (!accounts.current(actor.username()).equals(actor)) throw new BusinessException(ErrorCode.SESSION_INVALID);
    }
    private void requireAdmin(Actor actor) {
        active(actor);
        if (!actor.canModerate()) throw new BusinessException(ErrorCode.FORBIDDEN);
    }
}
