package com.creator.video.web;

import com.creator.common.*;
import com.creator.video.application.PublicVideoService;
import com.creator.video.domain.VideoSearch;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/videos")
public class PublicVideoController {
    private final PublicVideoService videos;
    public PublicVideoController(PublicVideoService videos) { this.videos = videos; }
    @GetMapping
    public PageResult<PublicVideoView> list(@RequestParam(defaultValue = "") String title, @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        var result = videos.list(new VideoSearch(title, categoryId), new PageRequest(page, size));
        return new PageResult<>(result.items().stream().map(PublicVideoView::from).toList(), result.total(), result.page(), result.size());
    }
    @GetMapping("/{id}")
    public PublicVideoView get(@PathVariable long id) { return PublicVideoView.from(videos.get(id)); }
}
