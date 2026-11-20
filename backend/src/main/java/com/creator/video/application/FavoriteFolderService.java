package com.creator.video.application;

import com.creator.audit.application.AuditLog;
import com.creator.common.*;
import com.creator.identity.application.AccountQueryService;
import com.creator.identity.domain.Actor;
import com.creator.video.domain.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FavoriteFolderService {
    private static final int MAX_FOLDERS = 20;
    private static final int MAX_NAME = 30;
    private final FavoriteFolderRepository folders;
    private final FavoriteService favorites;
    private final VideoQueryRepository videos;
    private final AccountQueryService accounts;
    private final AuditLog audit;

    public FavoriteFolderService(FavoriteFolderRepository folders, FavoriteService favorites,
            VideoQueryRepository videos, AccountQueryService accounts, AuditLog audit) {
        this.folders = folders;
        this.favorites = favorites;
        this.videos = videos;
        this.accounts = accounts;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<FavoriteFolder> list(Actor actor) {
        active(actor);
        return folders.list(actor.id());
    }

    @Transactional(timeout = 5)
    public FavoriteFolder create(Actor actor, String name) {
        active(actor);
        String clean = cleanName(name);
        if (folders.count(actor.id()) >= MAX_FOLDERS) throw new BusinessException(ErrorCode.FOLDER_LIMIT_REACHED);
        try {
            folders.create(actor.id(), clean);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.FOLDER_NAME_TAKEN);
        }
        audit.record(actor, "FAVORITE_FOLDER_CREATED", "FAVORITE_FOLDER", 0L);
        return owned(actor, folders.list(actor.id()).stream()
                .filter(folder -> folder.name().equals(clean)).findFirst().orElseThrow().id(), false);
    }

    @Transactional(timeout = 5)
    public FavoriteFolder rename(Actor actor, long id, long version, String name) {
        active(actor);
        String clean = cleanName(name);
        var folder = owned(actor, id, true);
        if (version < 0 || folder.version() != version) throw new BusinessException(ErrorCode.STALE_WRITE);
        try {
            if (!folders.rename(actor.id(), id, version, clean)) throw new BusinessException(ErrorCode.STALE_WRITE);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.FOLDER_NAME_TAKEN);
        }
        audit.record(actor, "FAVORITE_FOLDER_UPDATED", "FAVORITE_FOLDER", id);
        return owned(actor, id, false);
    }

    @Transactional(timeout = 5)
    public void delete(Actor actor, long id) {
        active(actor);
        owned(actor, id, false);
        if (!folders.delete(actor.id(), id)) throw new BusinessException(ErrorCode.STALE_WRITE);
        audit.record(actor, "FAVORITE_FOLDER_DELETED", "FAVORITE_FOLDER", id);
    }

    @Transactional(timeout = 5)
    public void addVideo(Actor actor, long id, long videoId) {
        active(actor);
        if (videoId <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        owned(actor, id, false);
        videos.findPublic(videoId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        favorites.add(actor, videoId);
        folders.addItem(id, videoId);
    }

    @Transactional(timeout = 5)
    public void removeVideo(Actor actor, long id, long videoId) {
        active(actor);
        if (videoId <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        owned(actor, id, false);
        folders.removeItem(id, videoId);
    }

    @Transactional(readOnly = true)
    public PageResult<PublicVideo> videos(Actor actor, long id, PageRequest page) {
        active(actor);
        owned(actor, id, false);
        return new PageResult<>(folders.items(id, page), folders.itemCount(id), page.page(), page.size());
    }

    private FavoriteFolder owned(Actor actor, long id, boolean lock) {
        if (id <= 0) throw new BusinessException(ErrorCode.NOT_FOUND);
        return folders.findOwned(actor.id(), id, lock).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private String cleanName(String name) {
        String clean = name == null ? "" : name.strip().replaceAll("\\s+", " ");
        if (clean.isEmpty() || clean.codePointCount(0, clean.length()) > MAX_NAME
                || !StandardCharsets.UTF_8.newEncoder().canEncode(clean)
                || clean.codePoints().anyMatch(Character::isISOControl)) {
            throw new BusinessException(ErrorCode.INVALID_FOLDER_NAME);
        }
        return clean;
    }

    private void active(Actor actor) {
        if (actor == null) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        if (!accounts.current(actor.username()).equals(actor)) throw new BusinessException(ErrorCode.SESSION_INVALID);
    }
}
