package com.creator.audit.infrastructure;

import com.creator.audit.application.AuditLog;
import com.creator.identity.domain.Actor;
import org.slf4j.MDC;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisAuditLog implements AuditLog {
    private final AuditLogMapper mapper;

    public MyBatisAuditLog(AuditLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void record(Actor actor, String action, String targetType, long targetId, String reason) {
        mapper.insert(actor.id(), action, targetType, targetId, MDC.get("requestId"), reason);
    }
}
