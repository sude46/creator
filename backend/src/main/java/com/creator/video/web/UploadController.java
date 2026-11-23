package com.creator.video.web;

import com.creator.identity.domain.Actor;
import com.creator.video.application.UploadService;
import com.creator.video.application.UploadTransactions;
import com.creator.video.domain.UploadSession;
import com.creator.video.domain.UploadVideo;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name = "creator.upload.enabled", havingValue = "true")
@RequestMapping("/api/creator/videos/{id}")
public class UploadController {
    private final UploadService uploads;
    private final UploadTransactions transactions;
    public UploadController(UploadService uploads, UploadTransactions transactions) {
        this.uploads = uploads;
        this.transactions = transactions;
    }
    @PutMapping(value = "/source", consumes = "video/mp4")
    public UploadView upload(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @RequestHeader("X-Video-Version") long version, HttpServletRequest request) throws IOException {
        return UploadView.from(uploads.upload(actor, id, version, request.getInputStream(), request.getContentLengthLong()));
    }
    @PostMapping(value = "/source/uploads", consumes = "application/json")
    public UploadSessionView start(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @RequestHeader("X-Video-Version") long version, @RequestBody StartRequest body) {
        return UploadSessionView.from(uploads.start(actor, id, version, body.size(), body.fingerprint()));
    }
    @GetMapping("/source/uploads/{uploadId}")
    public UploadSessionView session(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @PathVariable String uploadId) {
        return UploadSessionView.from(transactions.session(actor, id, uploadId));
    }
    @PutMapping(value = "/source/uploads/{uploadId}/chunks/{index}", consumes = "application/octet-stream")
    public UploadSessionView chunk(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @PathVariable String uploadId, @PathVariable int index, HttpServletRequest request) throws IOException {
        return UploadSessionView.from(uploads.chunk(actor, id, uploadId, index, request.getInputStream(),
                request.getContentLengthLong()));
    }
    @PostMapping("/source/uploads/{uploadId}/complete")
    public UploadView complete(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @PathVariable String uploadId, @RequestHeader("X-Video-Version") long version) {
        return UploadView.from(uploads.complete(actor, id, uploadId, version));
    }
    @GetMapping("/upload")
    public UploadView status(@RequestAttribute("currentActor") Actor actor, @PathVariable long id) {
        return UploadView.from(transactions.status(actor, id));
    }
    public record StartRequest(long size, String fingerprint) { }
    public record UploadView(String videoId, long version, boolean sourceUploaded, String processingStatus) {
        static UploadView from(UploadVideo video) {
            return new UploadView(video.id().toString(), video.version(), video.sourceAssetId() != null, video.processingStatus());
        }
    }
    public record UploadSessionView(String uploadId, String videoId, long size, int chunkSize, List<Integer> uploaded,
            long receivedBytes, String expiresAt) {
        static UploadSessionView from(UploadSession session) {
            var attempt = session.attempt();
            return new UploadSessionView(attempt.id(), attempt.videoId().toString(), attempt.declaredSize(),
                    attempt.chunkSize(), session.chunks().stream().map(chunk -> chunk.chunkIndex()).toList(),
                    session.receivedBytes(), attempt.expiresAt().toString());
        }
    }
}
