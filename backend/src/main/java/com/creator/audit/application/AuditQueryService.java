package com.creator.audit.application;

import com.creator.audit.domain.AuditQueryRepository;
import com.creator.audit.domain.AuditRecord;
import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.common.PageRequest;
import com.creator.common.PageResult;
import com.creator.identity.application.AccountQueryService;
import com.creator.identity.domain.Actor;
import com.creator.identity.domain.Role;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditQueryService {
    private final AuditQueryRepository audit;
    private final AccountQueryService accounts;

    public AuditQueryService(AuditQueryRepository audit, AccountQueryService accounts) {
        this.audit = audit;
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public PageResult<AuditRecord> list(Actor actor, Long actorId, String action, String targetType,
            LocalDateTime from, LocalDateTime to, PageRequest page) {
        requireAdmin(actor);
        return new PageResult<>(audit.findPage(actorId, clean(action), clean(targetType), from, to, page),
                audit.count(actorId, clean(action), clean(targetType), from, to), page.page(), page.size());
    }

    @Transactional(readOnly = true)
    public List<AuditRecord> export(Actor actor, Long actorId, String action, String targetType,
            LocalDateTime from, LocalDateTime to) {
        requireAdmin(actor);
        var result = new java.util.ArrayList<AuditRecord>();
        for (int page = 1; page <= 20; page++) {
            var batch = audit.findPage(actorId, clean(action), clean(targetType), from, to,
                    new PageRequest(page, PageRequest.MAX_SIZE));
            result.addAll(batch);
            if (batch.size() < PageRequest.MAX_SIZE) break;
        }
        return result;
    }

    private void requireAdmin(Actor actor) {
        if (actor == null) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        if (actor.role() != Role.ADMIN) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (!accounts.current(actor.username()).equals(actor)) throw new BusinessException(ErrorCode.SESSION_INVALID);
    }

    private String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.length() > 64) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return trimmed;
    }
}
