package com.creator.admin.web;

import com.creator.admin.application.AdminOverviewService;
import com.creator.admin.domain.AdminOverview;
import com.creator.identity.domain.Actor;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/overview")
public class AdminOverviewController {
    private final AdminOverviewService overview;

    public AdminOverviewController(AdminOverviewService overview) {
        this.overview = overview;
    }

    @GetMapping
    public OverviewView get(@RequestAttribute("currentActor") Actor actor) {
        var result = overview.get(actor);
        return new OverviewView(Long.toString(result.pendingReviews()), Long.toString(result.failedProcessing()),
                Long.toString(result.todayUsers()), Long.toString(result.todayDecisions()),
                result.recentDecisions().stream().map(DecisionView::from).toList());
    }

    public record OverviewView(String pendingReviews, String failedProcessing, String todayUsers,
            String todayDecisions, List<DecisionView> recentDecisions) {
    }

    public record DecisionView(String id, String videoId, String reviewerId, String outcome, String reason,
            String title, LocalDateTime createdAt) {
        static DecisionView from(AdminOverview.RecentDecision decision) {
            return new DecisionView(Long.toString(decision.id()), Long.toString(decision.videoId()),
                    Long.toString(decision.reviewerId()), decision.outcome(), decision.reason(),
                    decision.title(), decision.createdAt());
        }
    }
}
