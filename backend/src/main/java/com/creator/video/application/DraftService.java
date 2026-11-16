package com.creator.video.application;

import com.creator.audit.application.AuditLog;
import com.creator.catalog.application.CategorySelection;
import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.common.PageRequest;
import com.creator.common.PageResult;
import com.creator.identity.domain.Actor;
import com.creator.video.domain.DraftCreation;
import com.creator.video.domain.DraftRepository;
import com.creator.video.domain.VideoDraft;
import com.creator.video.domain.VideoMetadata;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DraftService {
    private static final int WRITE_TIMEOUT_SECONDS = 5;
    private final DraftRepository drafts;
    private final CategorySelection categories;
    private final AuditLog audit;

    public DraftService(DraftRepository drafts, CategorySelection categories, AuditLog audit) {
        this.drafts = drafts;
        this.categories = categories;
        this.audit = audit;
    }

    @Transactional(timeout = WRITE_TIMEOUT_SECONDS)
    public VideoDraft create(Actor actor, String requestKey, VideoMetadata metadata) {
        String key = normalizeKey(requestKey);
        var existing = drafts.findCreation(actor.id(), key, false);
        if (existing.isPresent()) return replay(existing.get(), metadata);
        categories.requireEnabled(metadata.categoryId());
        boolean created = drafts.insert(actor.id(), key, metadata);
        var creation = drafts.findCreation(actor.id(), key, true).orElseThrow();
        var result = replay(creation, metadata);
        if (created) audit.record(actor, "DRAFT_CREATED", "VIDEO", result.id());
        return result;
    }

    @Transactional(readOnly = true)
    public VideoDraft get(Actor actor, long id) {
        return drafts.findOwned(actor.id(), id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public PageResult<VideoDraft> list(Actor actor, PageRequest page) {
        return new PageResult<>(drafts.findPage(actor.id(), page), drafts.count(actor.id()), page.page(), page.size());
    }

    @Transactional(timeout = WRITE_TIMEOUT_SECONDS)
    public VideoDraft update(Actor actor, long id, long version, VideoMetadata metadata) {
        var draft = get(actor, id);
        if (draft.metadata().categoryId() != metadata.categoryId()) categories.requireEnabled(metadata.categoryId());
        if (version < 0 || !drafts.update(actor.id(), id, version, metadata)) {
            throw new BusinessException(ErrorCode.STALE_WRITE);
        }
        audit.record(actor, "DRAFT_UPDATED", "VIDEO", id);
        return new VideoDraft(id, actor.id(), metadata, version + 1);
    }

    private VideoDraft replay(DraftCreation creation, VideoMetadata metadata) {
        if (!creation.accessible()) throw new BusinessException(ErrorCode.NOT_FOUND);
        if (!creation.matches(metadata)) throw new BusinessException(ErrorCode.REQUEST_KEY_CONFLICT);
        return creation.draft();
    }

    private String normalizeKey(String key) {
        try {
            var parsed = UUID.fromString(key);
            if (!parsed.toString().equalsIgnoreCase(key)) throw new IllegalArgumentException();
            return parsed.toString();
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_KEY);
        }
    }
}
