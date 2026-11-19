package com.creator.audit.infrastructure;

import com.creator.audit.domain.AuditQueryRepository;
import com.creator.audit.domain.AuditRecord;
import com.creator.common.PageRequest;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisAuditQueryRepository implements AuditQueryRepository {
    private final AuditQueryMapper mapper;

    public MyBatisAuditQueryRepository(AuditQueryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<AuditRecord> findPage(Long actorId, String action, String targetType,
            LocalDateTime from, LocalDateTime to, PageRequest page) {
        return mapper.findPage(actorId, action, targetType, from, to, page.size(), page.offset());
    }

    @Override
    public long count(Long actorId, String action, String targetType, LocalDateTime from, LocalDateTime to) {
        return mapper.count(actorId, action, targetType, from, to);
    }
}
