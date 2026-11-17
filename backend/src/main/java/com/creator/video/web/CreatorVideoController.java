package com.creator.video.web;

import com.creator.common.PageRequest;
import com.creator.common.PageResult;
import com.creator.identity.domain.Actor;
import com.creator.video.application.CreatorVideoService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/creator/videos")
public class CreatorVideoController {
    private final CreatorVideoService videos;
    public CreatorVideoController(CreatorVideoService videos) { this.videos = videos; }
    @GetMapping("/{id}")
    public CreatorVideoView get(@RequestAttribute("currentActor") Actor actor, @PathVariable long id) {
        return CreatorVideoView.from(videos.get(actor, id));
    }
    @GetMapping
    public PageResult<CreatorVideoView> list(@RequestAttribute("currentActor") Actor actor,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        var result = videos.list(actor, new PageRequest(page, size));
        return new PageResult<>(result.items().stream().map(CreatorVideoView::from).toList(), result.total(), result.page(), result.size());
    }
    @PostMapping("/{id}/processing-retry")
    public CreatorVideoView retry(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @Valid @RequestBody VersionRequest request) {
        return CreatorVideoView.from(videos.retry(actor, id, request.version()));
    }
}
