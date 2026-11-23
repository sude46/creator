package com.creator.video.application;

import com.creator.common.*;
import com.creator.identity.application.AccountQueryService;
import com.creator.identity.domain.Actor;
import com.creator.video.domain.Notification;
import com.creator.video.domain.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private final NotificationRepository notifications;
    private final AccountQueryService accounts;
    public NotificationService(NotificationRepository notifications, AccountQueryService accounts) {
        this.notifications = notifications; this.accounts = accounts;
    }
    public void review(long userId, boolean approved, String reason, long videoId) {
        notifications.add(userId, "REVIEW", approved ? "作品审核通过" : "作品未通过",
                reason == null ? "" : reason, "/studio/" + videoId);
    }
    public void comment(long userId, String author, long videoId) {
        aggregateComment(userId, "COMMENT:" + videoId + ":root", author,
                author + " 评论了你的作品", false, "/videos/" + videoId);
    }
    public void commentReply(long userId, String author, long videoId, Long parentId, String link) {
        aggregateComment(userId, "COMMENT:" + videoId + ":reply", author,
                author + " 回复了你的评论", true, link);
    }
    private void aggregateComment(long userId, String key, String author, String firstTitle, boolean reply, String link) {
        var existing = notifications.findAggregate(userId, "COMMENT", key);
        if (existing != null) {
            int count = existing.count() + 1;
            notifications.updateAggregated(existing.id(), count + (reply ? " 条新回复" : " 条新评论"),
                    "最新来自 " + author, count, java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
        } else {
            notifications.addAggregated(userId, "COMMENT", key, firstTitle, "", link);
        }
    }
    public void reportResolved(long userId, boolean removed, String reason, String link) {
        String message = reason == null || reason.isBlank() ? "管理员已处理你的举报。" : reason;
        notifications.add(userId, "REPORT", removed ? "举报已处理：内容已删除" : "举报已处理：未发现违规", message, link);
    }
    public void appealResolved(long userId, boolean accepted, String link, String reason) {
        notifications.add(userId, "APPEAL", accepted ? "申诉已受理" : "申诉已驳回",
                reason == null ? "" : reason, link);
    }
    public void moderation(long userId, long commentId, String reason) {
        notifications.add(userId, "MODERATION", "评论已被删除",
                reason == null || reason.isBlank() ? "你可以在站内提交申诉。" : reason,
                "/comments/" + commentId + "/appeal");
    }
    public void follow(long userId, String follower) {
        notifications.add(userId, "FOLLOW", follower + " 关注了你", "", "/authors/" + userId);
    }
    public void mention(long userId, String author, long videoId) {
        notifications.add(userId, "MENTION", author + " 在评论中提到了你", "打开作品查看评论。", "/videos/" + videoId);
    }
    @Transactional(readOnly = true)
    public PageResult<Notification> list(Actor actor, boolean unreadOnly, String type, PageRequest page) {
        active(actor);
        String cleanType = type == null || type.isBlank() ? null : type.trim().toUpperCase();
        if (cleanType != null && !(cleanType.equals("REVIEW") || cleanType.equals("COMMENT")
                || cleanType.equals("FOLLOW") || cleanType.equals("REPORT") || cleanType.equals("APPEAL")
                || cleanType.equals("MODERATION") || cleanType.equals("MENTION"))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return new PageResult<>(notifications.page(actor.id(), unreadOnly, cleanType, page),
                notifications.count(actor.id(), unreadOnly, cleanType), page.page(), page.size());
    }
    public PageResult<Notification> list(Actor actor, PageRequest page) { return list(actor, false, null, page); }
    @Transactional(readOnly = true)
    public long unread(Actor actor) { active(actor); return notifications.unread(actor.id()); }
    @Transactional(timeout = 5)
    public void markRead(Actor actor, long id) { active(actor); notifications.markRead(actor.id(), id); }
    @Transactional(timeout = 5)
    public void markAll(Actor actor) { active(actor); notifications.markAll(actor.id()); }
    @Transactional(timeout = 5)
    public void delete(Actor actor, long id) {
        active(actor);
        if (id <= 0 || !notifications.delete(actor.id(), id)) throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    @Transactional(timeout = 5)
    public void clearRead(Actor actor) { active(actor); notifications.clearRead(actor.id()); }
    private void active(Actor actor) {
        if (actor == null) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        if (!accounts.current(actor.username()).equals(actor)) throw new BusinessException(ErrorCode.SESSION_INVALID);
    }
}
