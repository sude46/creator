package com.creator.video.application;

import com.creator.common.*;
import com.creator.identity.application.AccountQueryService;
import com.creator.identity.domain.Actor;
import com.creator.video.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CollectionService {
    private final CollectionRepository collections;
    private final AccountQueryService accounts;

    public CollectionService(CollectionRepository collections, AccountQueryService accounts) {
        this.collections = collections;
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public PageResult<VideoCollection> list(Actor actor, PageRequest page) {
        active(actor);
        return new PageResult<>(collections.ownPage(actor.id(), page), collections.ownCount(actor.id()), page.page(), page.size());
    }

    @Transactional(timeout = 5)
    public VideoCollection create(Actor actor, String title, String description, String visibility) {
        active(actor);
        var clean = validate(title, description, visibility);
        long id = collections.insert(actor.id(), clean.title(), clean.description(), clean.visibility());
        return collections.findOwned(actor.id(), id, false).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(timeout = 5)
    public VideoCollection update(Actor actor, long id, long version, String title, String description, String visibility) {
        active(actor);
        var current = collections.findOwned(actor.id(), id, true).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (current.version() != version) throw new BusinessException(ErrorCode.STALE_WRITE);
        var clean = validate(title, description, visibility);
        if (!collections.update(actor.id(), id, version, clean.title(), clean.description(), clean.visibility())) {
            throw new BusinessException(ErrorCode.STALE_WRITE);
        }
        return collections.findOwned(actor.id(), id, false).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(timeout = 5)
    public void delete(Actor actor, long id, long version) {
        active(actor);
        var current = collections.findOwned(actor.id(), id, true).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (current.version() != version) throw new BusinessException(ErrorCode.STALE_WRITE);
        collections.deleteItems(id);
        if (!collections.delete(actor.id(), id, version)) throw new BusinessException(ErrorCode.STALE_WRITE);
    }

    @Transactional(timeout = 5)
    public void add(Actor actor, long id, long videoId) {
        active(actor);
        collections.findOwned(actor.id(), id, true).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!collections.videoOwned(actor.id(), videoId)) throw new BusinessException(ErrorCode.NOT_FOUND);
        if (collections.itemExists(id, videoId)) return;
        collections.addItem(id, videoId, collections.nextOrder(id));
    }

    @Transactional(timeout = 5)
    public void remove(Actor actor, long id, long videoId) {
        active(actor);
        collections.findOwned(actor.id(), id, true).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!collections.removeItem(id, videoId)) throw new BusinessException(ErrorCode.NOT_FOUND);
    }

    private void active(Actor actor) {
        if (actor == null) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        if (!accounts.current(actor.username()).equals(actor)) throw new BusinessException(ErrorCode.SESSION_INVALID);
    }

    private Clean validate(String title, String description, String visibility) {
        String t = title == null ? "" : title.trim();
        String d = description == null ? "" : description.trim();
        String v = visibility == null ? "PUBLIC" : visibility.trim().toUpperCase();
        if (t.isEmpty() || t.length() > 80 || d.length() > 500 || !(v.equals("PUBLIC") || v.equals("PRIVATE"))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return new Clean(t, d, v);
    }

    private record Clean(String title, String description, String visibility) { }
}
