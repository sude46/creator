package com.creator.video.web;

import com.creator.common.PageRequest;
import com.creator.common.PageResult;
import com.creator.identity.domain.Actor;
import com.creator.video.application.CreatorVideoService;
import com.creator.video.application.ReviewService;
import com.creator.video.application.VideoDeletionService;
import com.creator.video.application.VideoProcessingService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/creator/videos")
public class CreatorVideoController {
    private final CreatorVideoService videos;
    private final ReviewService reviews;
    private final VideoDeletionService deletions;
    private final VideoProcessingService processing;
    public CreatorVideoController(CreatorVideoService videos, ReviewService reviews, VideoDeletionService deletions,
            VideoProcessingService processing) {
        this.videos = videos;
        this.reviews = reviews;
        this.deletions = deletions;
        this.processing = processing;
    }
    @GetMapping("/{id}")
    public CreatorVideoView get(@RequestAttribute("currentActor") Actor actor, @PathVariable long id) {
        return CreatorVideoView.from(videos.get(actor, id));
    }
    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(@RequestAttribute("currentActor") Actor actor) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"videos.csv\"")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(videos.exportCsv(actor));
    }

    @GetMapping
    public PageResult<CreatorVideoView> list(@RequestAttribute("currentActor") Actor actor,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        var result = videos.list(actor, new PageRequest(page, size));
        return new PageResult<>(result.items().stream().map(CreatorVideoView::from).toList(), result.total(), result.page(), result.size());
    }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @RequestParam long version) {
        deletions.deleteByOwner(actor, id, version);
    }
    @PostMapping("/{id}/cancel-processing")
    public CreatorVideoView cancelProcessing(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @Valid @RequestBody VersionRequest request) {
        return CreatorVideoView.from(processing.cancelByOwner(actor, id, request.version()));
    }
    @PostMapping("/{id}/processing-retry")
    public CreatorVideoView retry(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @Valid @RequestBody VersionRequest request) {
        return CreatorVideoView.from(videos.retry(actor, id, request.version()));
    }
    @PutMapping("/{id}")
    public CreatorVideoView update(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @Valid @RequestBody UpdateVideoRequest request) {
        return CreatorVideoView.from(reviews.update(actor, id, request.version(), request.metadata()));
    }
    @PostMapping("/{id}/review-submissions")
    public CreatorVideoView submit(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @Valid @RequestBody VersionRequest request) {
        return CreatorVideoView.from(reviews.submit(actor, id, request.version()));
    }
    @PostMapping("/{id}/review-withdrawal")
    public CreatorVideoView withdraw(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @Valid @RequestBody VersionRequest request) {
        return CreatorVideoView.from(reviews.withdraw(actor, id, request.version()));
    }
    @GetMapping("/{id}/reviews")
    public PageResult<ReviewRecordView> history(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        var result = reviews.ownerHistory(actor, id, new PageRequest(page, size));
        return new PageResult<>(result.items().stream().map(ReviewRecordView::from).toList(), result.total(), result.page(), result.size());
    }
}
