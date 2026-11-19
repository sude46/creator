package com.creator.video.web;

import com.creator.identity.domain.Actor;
import com.creator.video.application.*;
import com.creator.video.domain.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class MediaController {
    private final MediaAuthorizationService media;
    public MediaController(MediaAuthorizationService media) { this.media = media; }
    @PostMapping("/api/videos/{id}/media")
    public ResponseEntity<MediaSigner.SignedMedia> publicMedia(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @RequestParam MediaKind kind) {
        return grant(actor, id, MediaPurpose.PUBLIC_PLAY, kind, null, null, false);
    }
    @PostMapping("/api/creator/videos/{id}/preview")
    public ResponseEntity<MediaSigner.SignedMedia> ownerPreview(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @RequestParam MediaKind kind) {
        return grant(actor, id, MediaPurpose.OWNER_PREVIEW, kind, null, null, false);
    }
    @PostMapping("/api/admin/reviews/{id}/preview")
    public ResponseEntity<MediaSigner.SignedMedia> reviewPreview(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @RequestParam MediaKind kind, @RequestParam long version, @RequestParam long reviewRound) {
        return grant(actor, id, MediaPurpose.REVIEW_PREVIEW, kind, version, reviewRound, false);
    }
    @PostMapping("/api/creator/videos/{id}/download")
    public ResponseEntity<MediaSigner.SignedMedia> ownerDownload(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @RequestParam MediaKind kind) {
        return grant(actor, id, MediaPurpose.OWNER_DOWNLOAD, kind, null, null, true);
    }
    @PostMapping("/api/admin/reviews/{id}/download")
    public ResponseEntity<MediaSigner.SignedMedia> reviewDownload(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @RequestParam MediaKind kind, @RequestParam long version, @RequestParam long reviewRound) {
        return grant(actor, id, MediaPurpose.REVIEW_DOWNLOAD, kind, version, reviewRound, true);
    }
    private ResponseEntity<MediaSigner.SignedMedia> grant(Actor actor, long id, MediaPurpose purpose, MediaKind kind,
            Long version, Long round, boolean attachment) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma", "no-cache")
                .body(media.authorize(actor, id, purpose, kind, version, round, attachment));
    }
}
