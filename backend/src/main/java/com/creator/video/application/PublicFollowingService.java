package com.creator.video.application;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.common.PageRequest;
import com.creator.common.PageResult;
import com.creator.identity.application.AccountQueryService;
import com.creator.identity.domain.Actor;
import com.creator.video.domain.PublicVideo;
import com.creator.video.domain.VideoQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicFollowingService {
    private final VideoQueryRepository videos;
    private final AccountQueryService accounts;
    public PublicFollowingService(VideoQueryRepository videos, AccountQueryService accounts) {
        this.videos = videos; this.accounts = accounts;
    }
    @Transactional(readOnly = true)
    public PageResult<PublicVideo> list(Actor actor, PageRequest page) {
        if (actor == null) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        if (!accounts.current(actor.username()).equals(actor)) throw new BusinessException(ErrorCode.SESSION_INVALID);
        return new PageResult<>(videos.findFollowingPage(actor.id(), page), videos.countFollowing(actor.id()), page.page(), page.size());
    }
}
