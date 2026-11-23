package com.creator.video.application;

import com.creator.audit.application.AuditLog;
import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.identity.application.AccountQueryService;
import com.creator.identity.domain.Actor;
import com.creator.video.domain.HlsBackfillRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HlsBackfillService {
    public record View(int enqueued, Map<String, Long> states) { }
    private static final String[] STATES = {"PENDING", "RUNNING", "RETRY_WAIT", "DONE", "FAILED"};
    private final HlsBackfillRepository tasks;
    private final AccountQueryService accounts;
    private final AuditLog audit;

    public HlsBackfillService(HlsBackfillRepository tasks, AccountQueryService accounts, AuditLog audit) {
        this.tasks = tasks;
        this.accounts = accounts;
        this.audit = audit;
    }

    @Transactional(timeout = 30)
    public View enqueue(Actor actor, int limit, Long videoId) {
        superAdmin(actor);
        if (limit < 1 || limit > 50) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        var eligible = videoId == null ? tasks.eligible(limit) : tasks.eligibleVideo(videoId);
        int enqueued = 0;
        for (long id : eligible) {
            String state = tasks.state(id);
            if (state == null) {
                tasks.insert(id);
                enqueued++;
            } else if (state.equals("FAILED") || state.equals("DONE")) {
                tasks.resetFailed(id);
                enqueued++;
            } else {
                continue;
            }
            audit.record(actor, "HLS_BACKFILL_ENQUEUED", "VIDEO", id);
        }
        return new View(enqueued, states());
    }

    @Transactional(readOnly = true)
    public View status(Actor actor) {
        superAdmin(actor);
        return new View(0, states());
    }

    private Map<String, Long> states() {
        var counts = new LinkedHashMap<String, Long>();
        for (String state : STATES) counts.put(state, 0L);
        for (var count : tasks.counts()) {
            if (counts.containsKey(count.state())) counts.put(count.state(), count.count());
        }
        return counts;
    }

    private void superAdmin(Actor actor) {
        if (actor == null) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        if (!accounts.current(actor.username()).equals(actor)) throw new BusinessException(ErrorCode.SESSION_INVALID);
        if (!actor.superAdmin()) throw new BusinessException(ErrorCode.FORBIDDEN);
    }
}
