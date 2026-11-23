package com.creator.video.web;

import com.creator.identity.domain.Actor;
import com.creator.video.application.SubtitleService;
import com.creator.video.domain.SubtitleRepository;
import java.nio.charset.StandardCharsets;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class SubtitleController {
    private static final MediaType VTT = MediaType.parseMediaType("text/vtt;charset=UTF-8");
    private final SubtitleService subtitles;
    public SubtitleController(SubtitleService subtitles) { this.subtitles = subtitles; }

    @PutMapping(value = "/api/creator/videos/{id}/subtitle", consumes = {"text/vtt", "text/plain"})
    public SubtitleView upload(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @RequestBody byte[] content) {
        return SubtitleView.from(subtitles.upload(actor, id, content));
    }

    @GetMapping("/api/creator/videos/{id}/subtitle")
    public SubtitleView metadata(@RequestAttribute("currentActor") Actor actor, @PathVariable long id) {
        return SubtitleView.from(subtitles.metadata(actor, id));
    }

    @DeleteMapping("/api/creator/videos/{id}/subtitle")
    public ResponseEntity<Void> delete(@RequestAttribute("currentActor") Actor actor, @PathVariable long id) {
        subtitles.delete(actor, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/api/videos/{id}/subtitle.vtt", produces = "text/vtt")
    public ResponseEntity<byte[]> publicSubtitle(@PathVariable long id) {
        return ResponseEntity.ok().contentType(VTT).cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache").body(subtitles.publicContent(id));
    }

    public record SubtitleView(boolean exists, long sizeBytes, String updatedAt) {
        static SubtitleView from(SubtitleRepository.Metadata metadata) {
            return new SubtitleView(metadata.updatedAt() != null, metadata.sizeBytes(),
                    metadata.updatedAt() == null ? null : metadata.updatedAt().toString());
        }
    }
}
