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
        notifications.add(userId, "COMMENT", author + " 评论了你的作品", "", "/videos/" + videoId);
    }
    public void commentReply(long userId, String author, long videoId, Long parentId, String link) {
        notifications.add(userId, "COMMENT", author + " 回复了你的评论", "", link);
    }
    public void follow(long userId, String follower) {
        notifications.add(userId, "FOLLOW", follower + " 关注了你", "", "/authors/" + userId);
    }
    @Transactional(readOnly = true)
    public PageResult<Notification> list(Actor actor, PageRequest page) {
        active(actor);
        return new PageResult<>(notifications.page(actor.id(), page), notifications.count(actor.id()), page.page(), page.size());
    }
    @Transactional(readOnly = true)
    public long unread(Actor actor) { active(actor); return notifications.unread(actor.id()); }
    @Transactional(timeout = 5)
    public void markRead(Actor actor, long id) { active(actor); notifications.markRead(actor.id(), id); }
    @Transactional(timeout = 5)
    public void markAll(Actor actor) { active(actor); notifications.markAll(actor.id()); }
    private void active(Actor actor) {
        if (actor == null) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        if (!accounts.current(actor.username()).equals(actor)) throw new BusinessException(ErrorCode.SESSION_INVALID);
    }
}
