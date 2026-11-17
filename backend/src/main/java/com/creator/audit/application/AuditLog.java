package com.creator.audit.application;

import com.creator.identity.domain.Actor;

public interface AuditLog {
    default void record(Actor actor, String action, String targetType, long targetId) {
        record(actor, action, targetType, targetId, null);
    }
    void record(Actor actor, String action, String targetType, long targetId, String reason);
}
