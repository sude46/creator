package com.creator.video.web;

import com.creator.common.*;
import com.creator.identity.domain.Actor;
import com.creator.video.application.BatchReviewService;
import com.creator.video.application.ReviewService;
import com.creator.video.domain.ReviewDecision;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/reviews")
public class ReviewController {
    private final ReviewService reviews;
    private final BatchReviewService batch;
    public ReviewController(ReviewService reviews, BatchReviewService batch) { this.reviews = reviews; this.batch = batch; }
    @GetMapping
    public PageResult<ReviewQueueItem> queue(@RequestAttribute("currentActor") Actor actor,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        var result = reviews.queue(actor, new PageRequest(page, size));
        return new PageResult<>(result.items().stream().map(video -> new ReviewQueueItem(Long.toString(video.ownerId()),
                CreatorVideoView.from(video))).toList(), result.total(), result.page(), result.size());
    }
    @GetMapping("/{id}")
    public CreatorVideoView get(@RequestAttribute("currentActor") Actor actor, @PathVariable long id) {
        return CreatorVideoView.from(reviews.pending(actor, id));
    }
    @PostMapping("/{id}/decision")
    public CreatorVideoView decide(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @Valid @RequestBody DecisionRequest request) {
        return CreatorVideoView.from(reviews.decide(actor, id, request.version(), request.reviewRound(), request.decision()));
    }
    @PostMapping("/batch")
    public BatchReviewService.BatchResult batch(@RequestAttribute("currentActor") Actor actor,
            @Valid @RequestBody BatchRequest request) {
        return batch.decide(actor, request.items().stream().map(item -> new BatchReviewService.Item(
                item.videoId(), item.version(), item.reviewRound(), item.outcome(), item.reason())).toList());
    }

    public record BatchRequest(@NotNull @Size(min = 1, max = 20) java.util.List<BatchItem> items) { }
    public record BatchItem(@NotNull @Min(1) Long videoId, @NotNull @Min(0) Long version,
            @NotNull @Min(1) Long reviewRound, @NotNull ReviewDecision.Outcome outcome, @Size(max = 500) String reason) { }

    @GetMapping("/{id}/history")
    public PageResult<ReviewRecordView> history(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        var result = reviews.adminHistory(actor, id, new PageRequest(page, size));
        return new PageResult<>(result.items().stream().map(ReviewRecordView::from).toList(), result.total(), result.page(), result.size());
    }
    public record ReviewQueueItem(String ownerId, CreatorVideoView video) { }
    public record DecisionRequest(@NotNull @Min(0) Long version, @NotNull @Min(1) Long reviewRound,
            @NotNull ReviewDecision.Outcome outcome, @Size(max = 500) String reason) {
        ReviewDecision decision() { return new ReviewDecision(outcome, reason); }
    }
}
