package com.creator.video.web;

import com.creator.identity.domain.Actor;
import com.creator.video.application.UploadService;
import com.creator.video.application.UploadTransactions;
import com.creator.video.domain.UploadVideo;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
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
    @GetMapping("/upload")
    public UploadView status(@RequestAttribute("currentActor") Actor actor, @PathVariable long id) {
        return UploadView.from(transactions.status(actor, id));
    }
    public record UploadView(String videoId, long version, boolean sourceUploaded, String processingStatus) {
        static UploadView from(UploadVideo video) {
            return new UploadView(video.id().toString(), video.version(), video.sourceAssetId() != null, video.processingStatus());
        }
    }
}
