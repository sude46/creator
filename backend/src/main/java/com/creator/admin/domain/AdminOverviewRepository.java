package com.creator.admin.domain;

import java.time.LocalDateTime;
import java.util.List;

public interface AdminOverviewRepository {
    long pendingReviews(long adminId);
    long failedProcessing();
    long usersCreatedSince(LocalDateTime start);
    List<AdminOverview.RecentDecision> recentDecisions(int limit);
}
