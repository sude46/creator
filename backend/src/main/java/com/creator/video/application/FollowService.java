package com.creator.video.application;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.identity.application.AccountQueryService;
import com.creator.identity.domain.Actor;
import com.creator.video.domain.FollowRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FollowService {
    private final FollowRepository follows;
    private final AccountQueryService accounts;
    private final ObjectProvider<NotificationService> notifications;
    private final ProfileService profiles;
    public FollowService(FollowRepository follows, AccountQueryService accounts, ObjectProvider<NotificationService> notifications,
            ProfileService profiles) {
        this.follows = follows; this.accounts = accounts; this.notifications = notifications; this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public Profile profile(Actor actor, long authorId) {
        active(actor);
        String username = follows.activeUsername(authorId);
        if (username == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        var details = profiles.summary(authorId);
        return new Profile(authorId, username, details.displayName(), details.bio(), details.website(), details.hasAvatar(),
                details.version(), follows.followerCount(authorId), follows.followingCount(authorId),
                authorId != actor.id() && follows.exists(actor.id(), authorId), authorId == actor.id());
    }

    @Transactional(timeout = 5)
    public void follow(Actor actor, long authorId) {
        active(actor);
        if (authorId == actor.id()) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        if (follows.activeUsername(authorId) == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        boolean already = follows.exists(actor.id(), authorId);
        follows.add(actor.id(), authorId);
        if (!already) notifications.ifAvailable(service -> service.follow(authorId, actor.username()));
    }

    @Transactional(timeout = 5)
    public void unfollow(Actor actor, long authorId) { active(actor); follows.remove(actor.id(), authorId); }

    private void active(Actor actor) {
        if (actor == null) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        if (!accounts.current(actor.username()).equals(actor)) throw new BusinessException(ErrorCode.SESSION_INVALID);
    }
    public record Profile(long id, String username, String displayName, String bio, String website, boolean hasAvatar,
            long profileVersion, long followerCount, long followingCount, boolean following, boolean self) { }
}
