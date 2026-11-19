package com.creator.admin.domain;

import java.time.LocalDateTime;
import java.util.List;

public record AdminOverview(long pendingReviews, long failedProcessing, long todayUsers,
        long todayDecisions, List<RecentDecision> recentDecisions) {
    public record RecentDecision(long id, long videoId, long reviewerId, String outcome, String reason,
            String title, LocalDateTime createdAt) {
    }
}
