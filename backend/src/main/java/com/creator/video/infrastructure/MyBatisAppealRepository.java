package com.creator.video.infrastructure;

import com.creator.common.PageRequest;
import com.creator.video.domain.AppealRepository;
import com.creator.video.domain.ContentAppeal;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisAppealRepository implements AppealRepository {
    private final AppealMapper mapper;
    public MyBatisAppealRepository(AppealMapper mapper) { this.mapper = mapper; }
    public void add(long appellantId, String targetType, long targetId, String reason) {
        mapper.add(appellantId, targetType, targetId, reason);
    }
    public boolean openExists(long appellantId, String targetType, long targetId) {
        return mapper.openExists(appellantId, targetType, targetId);
    }
    public List<ContentAppeal> page(Long appellantId, String targetType, Long targetId, String status, PageRequest page) {
        return mapper.page(appellantId, targetType, targetId, status, page.size(), page.offset());
    }
    public long count(Long appellantId, String targetType, Long targetId, String status) {
        return mapper.count(appellantId, targetType, targetId, status);
    }
    public Optional<ContentAppeal> lock(long id) { return Optional.ofNullable(mapper.lock(id)); }
    public boolean resolve(long id, long resolverId, String status, String reason) {
        return mapper.resolve(id, resolverId, status, reason) == 1;
    }
}
