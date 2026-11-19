package com.creator.video.web;

import com.creator.common.*;
import com.creator.identity.domain.Actor;
import com.creator.video.application.PublicationService;
import com.creator.video.application.VideoDeletionService;
import com.creator.video.application.VideoProcessingService;
import com.creator.video.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import org.springframework.web.bind.annotation.*;

@RestController
public class PublicationController {
    private final PublicationService publications;
    private final VideoDeletionService deletions;
    private final VideoProcessingService processing;
    public PublicationController(PublicationService publications, VideoDeletionService deletions,
            VideoProcessingService processing) {
        this.publications = publications;
        this.deletions = deletions;
        this.processing = processing;
    }

    @PostMapping("/api/creator/videos/{id}/offline")
    public CreatorVideoView offline(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @Valid @RequestBody VersionRequest request) {
        return CreatorVideoView.from(publications.offline(actor, id, request.version()));
    }
    @PostMapping("/api/creator/videos/{id}/publication")
    public CreatorVideoView publish(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @Valid @RequestBody VersionRequest request) {
        return CreatorVideoView.from(publications.publish(actor, id, request.version()));
    }
    @PostMapping("/api/admin/videos/{id}/block")
    public CreatorVideoView block(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @Valid @RequestBody ModerationRequest request) {
        return CreatorVideoView.from(publications.block(actor, id, request.version(), new ModerationReason(request.reason())));
    }
    @PostMapping("/api/admin/videos/{id}/unblock")
    public CreatorVideoView unblock(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @Valid @RequestBody ModerationRequest request) {
        return CreatorVideoView.from(publications.unblock(actor, id, request.version(), new ModerationReason(request.reason())));
    }
    @PostMapping("/api/admin/videos/{id}/cancel-processing")
    public CreatorVideoView cancelProcessing(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @Valid @RequestBody ModerationRequest request) {
        return CreatorVideoView.from(processing.cancelByAdmin(actor, id, request.version(),
                new ModerationReason(request.reason())));
    }
    @PostMapping("/api/admin/videos/{id}/delete")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void delete(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @Valid @RequestBody ModerationRequest request) {
        deletions.deleteByAdmin(actor, id, request.version(), new ModerationReason(request.reason()));
    }
    @GetMapping("/api/admin/videos")
    public PageResult<ManagedVideoView> list(@RequestAttribute("currentActor") Actor actor,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        var result = publications.managed(actor, new PageRequest(page, size));
        return new PageResult<>(result.items().stream().map(ManagedVideoView::from).toList(), result.total(), result.page(), result.size());
    }
    @GetMapping("/api/admin/videos/{id}")
    public ManagedVideoView get(@RequestAttribute("currentActor") Actor actor, @PathVariable long id) {
        return ManagedVideoView.from(publications.managed(actor, id));
    }
    @GetMapping("/api/creator/videos/{id}/moderation-history")
    public PageResult<ModerationRecordView> ownerHistory(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return history(publications.ownerHistory(actor, id, new PageRequest(page, size)));
    }
    @GetMapping("/api/admin/videos/{id}/moderation-history")
    public PageResult<ModerationRecordView> adminHistory(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return history(publications.adminHistory(actor, id, new PageRequest(page, size)));
    }
    private PageResult<ModerationRecordView> history(PageResult<ModerationRecord> result) {
        return new PageResult<>(result.items().stream().map(ModerationRecordView::from).toList(), result.total(), result.page(), result.size());
    }
    public record ModerationRequest(@NotNull @Min(0) Long version, @Size(max = 500) String reason) { }
    public record ManagedVideoView(String ownerId, CreatorVideoView video) {
        static ManagedVideoView from(CreatorVideo video) { return new ManagedVideoView(Long.toString(video.ownerId()), CreatorVideoView.from(video)); }
    }
    public record ModerationRecordView(String id, String actorId, String action, String reason, LocalDateTime createdAt) {
        static ModerationRecordView from(ModerationRecord record) {
            return new ModerationRecordView(Long.toString(record.id()), Long.toString(record.actorId()), record.action(), record.reason(), record.createdAt());
        }
    }
}
