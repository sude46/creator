package com.creator.video.web;

import com.creator.identity.domain.Actor;
import com.creator.video.application.HlsBackfillService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/maintenance")
public class HlsBackfillController {
    private final HlsBackfillService backfill;
    public HlsBackfillController(HlsBackfillService backfill) { this.backfill = backfill; }

    @PostMapping("/hls-backfill")
    public HlsBackfillService.View enqueue(@RequestAttribute("currentActor") Actor actor,
            @RequestParam(defaultValue = "20") int limit, @RequestParam(required = false) Long videoId) {
        return backfill.enqueue(actor, limit, videoId);
    }

    @GetMapping("/hls-backfill")
    public HlsBackfillService.View status(@RequestAttribute("currentActor") Actor actor) {
        return backfill.status(actor);
    }
}
