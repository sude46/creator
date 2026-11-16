package com.creator.audit.application;

import com.creator.identity.domain.Actor;

public interface AuditLog {
    void record(Actor actor, String action, String targetType, long targetId);
}
