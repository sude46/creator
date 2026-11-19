package com.creator.video.web;

import com.creator.common.*;
import com.creator.video.application.PublicVideoAnchorService;
import com.creator.video.application.PublicVideoService;
import com.creator.video.domain.VideoSearch;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/videos")
public class PublicVideoController {
    private final PublicVideoService videos;
    private final PublicVideoAnchorService anchors;
    public PublicVideoController(PublicVideoService videos, PublicVideoAnchorService anchors) {
        this.videos = videos;
        this.anchors = anchors;
    }
    @GetMapping(params = "!anchorId")
    public PageResult<PublicVideoView> list(@RequestParam(defaultValue = "") String title, @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        var result = videos.list(new VideoSearch(title, categoryId), new PageRequest(page, size));
        return new PageResult<>(result.items().stream().map(PublicVideoView::from).toList(), result.total(), result.page(), result.size());
    }

    @GetMapping(params = "anchorId")
    public AnchorView anchor(@RequestParam(defaultValue = "") String title, @RequestParam(required = false) Long categoryId,
            @RequestParam long anchorId, @RequestParam(defaultValue = "3") int before,
            @RequestParam(defaultValue = "4") int after) {
        var result = anchors.anchor(new VideoSearch(title, categoryId), anchorId, before, after);
        return new AnchorView(result.items().stream().map(PublicVideoView::from).toList(),
                result.anchorIndex(), result.hasBefore(), result.hasAfter());
    }

    public record AnchorView(java.util.List<PublicVideoView> items, Integer anchorIndex,
            boolean hasBefore, boolean hasAfter) { }
    @GetMapping("/{id}")
    public PublicVideoView get(@PathVariable long id) { return PublicVideoView.from(videos.get(id)); }

    @PostMapping("/{id}/view")
    public ViewCountView view(@PathVariable long id) { return new ViewCountView(videos.recordView(id)); }

    public record ViewCountView(long viewCount) { }
}
