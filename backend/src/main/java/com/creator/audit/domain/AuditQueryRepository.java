package com.creator.audit.domain;

import com.creator.common.PageRequest;
import java.time.LocalDateTime;
import java.util.List;

public interface AuditQueryRepository {
    List<AuditRecord> findPage(Long actorId, String action, String targetType,
            LocalDateTime from, LocalDateTime to, PageRequest page);

    long count(Long actorId, String action, String targetType, LocalDateTime from, LocalDateTime to);
}
