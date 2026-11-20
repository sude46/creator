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
public class FavoriteService {
    private final FavoriteRepository favorites;
    private final VideoQueryRepository videos;
    private final AccountQueryService accounts;
    private final FavoriteFolderRepository folders;

    public FavoriteService(FavoriteRepository favorites, VideoQueryRepository videos, AccountQueryService accounts,
            FavoriteFolderRepository folders) {
        this.favorites = favorites;
        this.videos = videos;
        this.accounts = accounts;
        this.folders = folders;
    }

    @Transactional(timeout = 5)
    public void add(Actor actor, long videoId) {
        active(actor);
        validId(videoId);
        videos.findPublic(videoId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        favorites.add(actor.id(), videoId);
    }

    @Transactional(timeout = 5)
    public void remove(Actor actor, long videoId) {
        active(actor);
        validId(videoId);
        favorites.remove(actor.id(), videoId);
        folders.removeVideoFromAll(actor.id(), videoId);
    }

    @Transactional(readOnly = true)
    public boolean contains(Actor actor, long videoId) {
        active(actor);
        validId(videoId);
        return favorites.contains(actor.id(), videoId);
    }

    @Transactional(readOnly = true)
    public PageResult<FavoriteItem> list(Actor actor, PageRequest page) {
        active(actor);
        var saved = favorites.findPage(actor.id(), page);
        var visible = videos.findPublicByIds(saved.stream().map(Favorite::videoId).toList()).stream()
                .collect(Collectors.toMap(PublicVideo::id, Function.identity()));
        var items = saved.stream().map(favorite -> new FavoriteItem(favorite.videoId(), favorite.createdAt(), visible.get(favorite.videoId()))).toList();
        return new PageResult<>(items, favorites.count(actor.id()), page.page(), page.size());
    }

    private void active(Actor actor) {
        if (actor == null) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        if (!accounts.current(actor.username()).equals(actor)) throw new BusinessException(ErrorCode.SESSION_INVALID);
    }
    private void validId(long videoId) { if (videoId <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST); }
}
