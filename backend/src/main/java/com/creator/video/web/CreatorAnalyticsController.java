package com.creator.video.web;

import com.creator.identity.domain.Actor;
import com.creator.video.application.CreatorAnalyticsService;
import com.creator.video.domain.CreatorAnalytics;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/creator/analytics")
public class CreatorAnalyticsController {
    private final CreatorAnalyticsService analytics;
    public CreatorAnalyticsController(CreatorAnalyticsService analytics) { this.analytics = analytics; }
    @GetMapping
    public CreatorAnalytics get(@RequestAttribute("currentActor") Actor actor,
            @RequestParam(defaultValue = "30") int days) {
        return analytics.get(actor, days);
    }
}
