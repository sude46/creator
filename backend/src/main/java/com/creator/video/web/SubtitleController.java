package com.creator.video.web;

import com.creator.identity.domain.Actor;
import com.creator.video.application.SubtitleService;
import com.creator.video.domain.SubtitleRepository;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class SubtitleController {
    private static final MediaType VTT = MediaType.parseMediaType("text/vtt;charset=UTF-8");
    private final SubtitleService subtitles;
    public SubtitleController(SubtitleService subtitles) { this.subtitles = subtitles; }

    @PutMapping(value = "/api/creator/videos/{id}/subtitles/{language}", consumes = {"text/vtt", "text/plain"})
    public SubtitleView upload(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @PathVariable String language, @RequestBody byte[] content) {
        return SubtitleView.from(subtitles.upload(actor, id, language, content));
    }

    @GetMapping("/api/creator/videos/{id}/subtitles")
    public SubtitleListView list(@RequestAttribute("currentActor") Actor actor, @PathVariable long id) {
        return new SubtitleListView(subtitles.list(actor, id).stream().map(SubtitleView::from).toList());
    }

    @DeleteMapping("/api/creator/videos/{id}/subtitles/{language}")
    public ResponseEntity<Void> delete(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @PathVariable String language) {
        subtitles.delete(actor, id, language);
        return ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/api/creator/videos/{id}/subtitle", consumes = {"text/vtt", "text/plain"})
    public SubtitleView uploadLegacy(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @RequestBody byte[] content) {
        return SubtitleView.from(subtitles.upload(actor, id, content));
    }

    @GetMapping("/api/creator/videos/{id}/subtitle")
    public SubtitleView metadataLegacy(@RequestAttribute("currentActor") Actor actor, @PathVariable long id) {
        return SubtitleView.from(subtitles.metadata(actor, id));
    }

    @DeleteMapping("/api/creator/videos/{id}/subtitle")
    public ResponseEntity<Void> deleteLegacy(@RequestAttribute("currentActor") Actor actor, @PathVariable long id) {
        subtitles.delete(actor, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/videos/{id}/subtitles")
    public SubtitleListView publicList(@PathVariable long id) {
        return new SubtitleListView(subtitles.publicList(id).stream().map(SubtitleView::from).toList());
    }

    @GetMapping(value = "/api/videos/{id}/subtitles/{language}.vtt", produces = "text/vtt")
    public ResponseEntity<byte[]> publicSubtitle(@PathVariable long id, @PathVariable String language) {
        return vtt(subtitles.publicContent(id, language));
    }

    @GetMapping(value = "/api/videos/{id}/subtitle.vtt", produces = "text/vtt")
    public ResponseEntity<byte[]> publicSubtitleLegacy(@PathVariable long id) {
        return vtt(subtitles.publicContent(id));
    }

    private ResponseEntity<byte[]> vtt(byte[] body) {
        return ResponseEntity.ok().contentType(VTT).cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache").body(body);
    }

    public record SubtitleListView(List<SubtitleView> items) { }
    public record SubtitleView(boolean exists, String language, String label, long sizeBytes, String updatedAt) {
        static SubtitleView from(SubtitleRepository.Metadata metadata) {
            return new SubtitleView(metadata.updatedAt() != null, metadata.language(), metadata.label(),
                    metadata.sizeBytes(), metadata.updatedAt() == null ? null : metadata.updatedAt().toString());
        }
    }
}
