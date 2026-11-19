package com.creator.video.application;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.identity.application.AccountQueryService;
import com.creator.identity.domain.Actor;
import com.creator.video.domain.LikeRepository;
import com.creator.video.domain.VideoQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LikeService {
    private final LikeRepository likes;
    private final VideoQueryRepository videos;
    private final AccountQueryService accounts;
    public LikeService(LikeRepository likes, VideoQueryRepository videos, AccountQueryService accounts) {
        this.likes = likes; this.videos = videos; this.accounts = accounts;
    }
    @Transactional(timeout = 5)
    public void add(Actor actor, long videoId) { active(actor); videos.findPublic(videoId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND)); likes.add(actor.id(), videoId); }
    @Transactional(timeout = 5)
    public void remove(Actor actor, long videoId) { active(actor); likes.remove(actor.id(), videoId); }
    @Transactional(readOnly = true)
    public boolean state(Actor actor, long videoId) { active(actor); return likes.exists(actor.id(), videoId); }
    private void active(Actor actor) {
        if (actor == null) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        if (!accounts.current(actor.username()).equals(actor)) throw new BusinessException(ErrorCode.SESSION_INVALID);
    }
}
