package com.creator.video.application;

import com.creator.common.*;
import com.creator.identity.application.AccountQueryService;
import com.creator.identity.domain.Actor;
import com.creator.video.domain.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HistoryService {
    private final ViewHistoryRepository histories;
    private final VideoQueryRepository videos;
    private final AccountQueryService accounts;

    public HistoryService(ViewHistoryRepository histories, VideoQueryRepository videos, AccountQueryService accounts) {
        this.histories = histories;
        this.videos = videos;
        this.accounts = accounts;
    }

    @Transactional(timeout = 5)
    public void record(long userId, long videoId) {
        if (userId <= 0 || videoId <= 0) return;
        histories.record(userId, videoId);
    }

    @Transactional(readOnly = true)
    public PageResult<HistoryItem> list(Actor actor, PageRequest page) {
        active(actor);
        var saved = histories.findPage(actor.id(), page);
        var visible = videos.findPublicByIds(saved.stream().map(ViewHistory::videoId).toList()).stream()
                .collect(Collectors.toMap(PublicVideo::id, Function.identity()));
        var items = saved.stream().map(entry -> new HistoryItem(entry.videoId(), entry.viewedAt(), visible.get(entry.videoId()))).toList();
        return new PageResult<>(items, histories.count(actor.id()), page.page(), page.size());
    }

    @Transactional(timeout = 5)
    public void remove(Actor actor, long videoId) {
        active(actor);
        if (videoId <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        histories.remove(actor.id(), videoId);
    }

    @Transactional(timeout = 5)
    public void clear(Actor actor) {
        active(actor);
        histories.clear(actor.id());
    }

    private void active(Actor actor) {
        if (actor == null) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        if (!accounts.current(actor.username()).equals(actor)) throw new BusinessException(ErrorCode.SESSION_INVALID);
    }
}
