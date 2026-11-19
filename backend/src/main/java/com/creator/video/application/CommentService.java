package com.creator.video.application;

import com.creator.audit.application.AuditLog;
import com.creator.common.*;
import com.creator.identity.application.AccountQueryService;
import com.creator.identity.domain.Actor;
import com.creator.identity.domain.Role;
import com.creator.video.domain.Comment;
import com.creator.video.domain.CommentRepository;
import com.creator.video.domain.VideoQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {
    private final CommentRepository comments;
    private final VideoQueryRepository videos;
    private final AccountQueryService accounts;
    private final AuditLog audit;
    private final ObjectProvider<NotificationService> notifications;
    public CommentService(CommentRepository comments, VideoQueryRepository videos, AccountQueryService accounts, AuditLog audit,
            ObjectProvider<NotificationService> notifications) {
        this.comments = comments; this.videos = videos; this.accounts = accounts; this.audit = audit; this.notifications = notifications;
    }
    @Transactional(readOnly = true)
    public PageResult<Comment> list(Actor actor, long videoId, PageRequest page) {
        active(actor); videos.findPublic(videoId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return new PageResult<>(comments.page(videoId, page), comments.count(videoId), page.page(), page.size());
    }
    @Transactional(timeout = 5)
    public Comment add(Actor actor, long videoId, String content) {
        active(actor); var video = videos.findPublic(videoId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        String clean = content == null ? "" : content.trim();
        if (clean.isEmpty() || clean.length() > 500) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        long id = comments.insert(videoId, actor.id(), clean);
        if (video.ownerId() != actor.id()) notifications.ifAvailable(service -> service.comment(video.ownerId(), actor.username(), videoId));
        return comments.find(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
    @Transactional(timeout = 5)
    public void delete(Actor actor, long id) {
        active(actor);
        var comment = comments.find(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (actor.role() != Role.ADMIN && actor.id() != comment.userId()) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (!comments.delete(id)) throw new BusinessException(ErrorCode.NOT_FOUND);
        audit.record(actor, "COMMENT_DELETED", "COMMENT", id);
    }
    private void active(Actor actor) {
        if (actor == null) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        if (!accounts.current(actor.username()).equals(actor)) throw new BusinessException(ErrorCode.SESSION_INVALID);
    }
}
