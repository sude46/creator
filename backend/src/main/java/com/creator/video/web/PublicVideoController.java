package com.creator.video.web;

import com.creator.common.*;
import com.creator.video.application.PublicVideoAnchorService;
import com.creator.video.application.LikeService;
import com.creator.video.application.PublicVideoService;
import com.creator.video.domain.VideoSearch;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/videos")
public class PublicVideoController {
    private final PublicVideoService videos;
    private final PublicVideoAnchorService anchors;
    private final LikeService likes;
    public PublicVideoController(PublicVideoService videos, PublicVideoAnchorService anchors, LikeService likes) {
        this.videos = videos;
        this.anchors = anchors;
        this.likes = likes;
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

    @PutMapping("/{id}/like")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void like(@RequestAttribute("currentActor") com.creator.identity.domain.Actor actor, @PathVariable long id) {
        likes.add(actor, id);
    }

    @DeleteMapping("/{id}/like")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void unlike(@RequestAttribute("currentActor") com.creator.identity.domain.Actor actor, @PathVariable long id) {
        likes.remove(actor, id);
    }

    @GetMapping("/{id}/like")
    public LikeState likeState(@RequestAttribute("currentActor") com.creator.identity.domain.Actor actor, @PathVariable long id) {
        return new LikeState(likes.state(actor, id));
    }

    public record LikeState(boolean liked) { }

    @PostMapping("/{id}/view")
    public ViewCountView view(@PathVariable long id) { return new ViewCountView(videos.recordView(id)); }

    public record ViewCountView(long viewCount) { }
}
