package com.creator.video.infrastructure;

import com.creator.common.PageRequest;
import com.creator.video.domain.*;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisReportRepository implements ReportRepository {
    private final ReportMapper mapper;
    public MyBatisReportRepository(ReportMapper mapper) { this.mapper = mapper; }
    public void add(long reporterId, String targetType, long targetId, String reason) { mapper.add(reporterId, targetType, targetId, reason); }
    public List<ContentReport> page(String status, PageRequest page) { return mapper.page(status, page.size(), page.offset()); }
    public long count(String status) { return mapper.count(status); }
    public Optional<ContentReport> lock(long id) { return Optional.ofNullable(mapper.lock(id)); }
    public boolean resolve(long id, long resolverId, String status, String reason) { return mapper.resolve(id, resolverId, status, reason) == 1; }
}
