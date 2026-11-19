package com.creator.admin.infrastructure;

import com.creator.admin.domain.AdminOverview;
import com.creator.admin.domain.AdminOverviewRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisAdminOverviewRepository implements AdminOverviewRepository {
    private final AdminOverviewMapper mapper;

    public MyBatisAdminOverviewRepository(AdminOverviewMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public long pendingReviews(long adminId) {
        return mapper.pendingReviews(adminId);
    }

    @Override
    public long failedProcessing() {
        return mapper.failedProcessing();
    }

    @Override
    public long usersCreatedSince(LocalDateTime start) {
        return mapper.usersCreatedSince(start);
    }

    @Override
    public List<AdminOverview.RecentDecision> recentDecisions(int limit) {
        return mapper.recentDecisions(limit);
    }
}
