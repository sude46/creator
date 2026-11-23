package com.creator.video.application;

import com.creator.audit.application.AuditLog;
import com.creator.common.*;
import com.creator.identity.application.AccountQueryService;
import com.creator.identity.domain.Actor;
import com.creator.identity.domain.Role;
import com.creator.video.domain.Comment;
import com.creator.video.domain.CommentAdmin;
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
        return list(actor, videoId, "latest", page);
    }
    @Transactional(readOnly = true)
    public PageResult<Comment> list(Actor actor, long videoId, String sort, PageRequest page) {
        if (actor != null) active(actor);
        videos.findPublic(videoId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        String order = sort == null ? "latest" : sort.trim().toLowerCase();
        if (!(order.equals("latest") || order.equals("oldest") || order.equals("hot"))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return new PageResult<>(comments.page(videoId, actor == null ? 0 : actor.id(), order, page),
                comments.count(videoId), page.page(), page.size());
    }
    @Transactional(readOnly = true)
    public PageResult<Comment> replies(Actor actor, long parentId, PageRequest page) {
        if (actor != null) active(actor);
        var parent = comments.find(parentId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        videos.findPublic(parent.videoId()).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return new PageResult<>(comments.replies(parentId, actor == null ? 0 : actor.id(), page),
                comments.replyCount(parentId), page.page(), page.size());
    }
    @Transactional(timeout = 5)
    public Comment add(Actor actor, long videoId, String content) { return add(actor, videoId, content, null); }
    @Transactional(timeout = 5)
    public Comment add(Actor actor, long videoId, String content, Long parentId) {
        active(actor); var video = videos.findPublic(videoId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        String clean = content == null ? "" : content.trim();
        if (clean.isEmpty() || clean.length() > 500) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        Long rootId = null;
        Comment parent = null;
        if (parentId != null) {
            parent = comments.find(parentId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
            if (parent.videoId() != videoId) throw new BusinessException(ErrorCode.INVALID_REQUEST);
            rootId = parent.parentId() == null ? parent.id() : parent.parentId();
        }
        long id = comments.insert(videoId, actor.id(), rootId, clean);
        Comment parentComment = parent;
        Long recipient = parentComment == null ? video.ownerId() : parentComment.userId();
        if (!recipient.equals(actor.id())) {
            String link = "/videos/" + videoId;
            if (parentComment == null) {
                notifications.ifAvailable(service -> service.comment(recipient, actor.username(), videoId));
            } else {
                notifications.ifAvailable(service -> service.commentReply(recipient, actor.username(), videoId,
                        parentComment.id(), link));
            }
        }
        return comments.find(id, actor.id()).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
    @Transactional(timeout = 5)
    public void delete(Actor actor, long id) { delete(actor, id, null); }
    @Transactional(timeout = 5)
    public void delete(Actor actor, long id, String reason) {
        active(actor);
        var comment = comments.find(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!actor.canModerate() && actor.id() != comment.userId()) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (!comments.delete(id)) throw new BusinessException(ErrorCode.NOT_FOUND);
        if (actor.id() != comment.userId()) {
            notifications.ifAvailable(service -> service.moderation(comment.userId(), id, reason));
        }
        audit.record(actor, "COMMENT_DELETED", "COMMENT", id);
    }
    @Transactional(readOnly = true)
    public CommentAdmin appealContext(Actor actor, long id) {
        active(actor);
        var comment = comments.findAny(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (comment.userId() != actor.id()) throw new BusinessException(ErrorCode.NOT_FOUND);
        return comment;
    }
    @Transactional(timeout = 5)
    public void like(Actor actor, long id) {
        active(actor);
        var comment = comments.find(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        videos.findPublic(comment.videoId()).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        comments.like(id, actor.id());
    }
    @Transactional(timeout = 5)
    public void unlike(Actor actor, long id) {
        active(actor);
        comments.unlike(id, actor.id());
    }
    @Transactional(readOnly = true)
    public boolean liked(Actor actor, long id) {
        active(actor);
        return comments.liked(id, actor.id());
    }
    private void active(Actor actor) {
        if (actor == null) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        if (!accounts.current(actor.username()).equals(actor)) throw new BusinessException(ErrorCode.SESSION_INVALID);
    }
}
