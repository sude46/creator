package com.creator.video.web;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.video.application.HlsPlaybackService;
import com.creator.video.application.ObjectStorage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HlsController {
    private final HlsPlaybackService hls;
    private final ObjectProvider<ObjectStorage> storage;
    public HlsController(HlsPlaybackService hls, ObjectProvider<ObjectStorage> storage) {
        this.hls = hls;
        this.storage = storage;
    }

    @GetMapping(value = "/api/videos/{id}/hls/master.m3u8", produces = "application/vnd.apple.mpegurl")
    public ResponseEntity<String> master(@PathVariable long id) {
        return playlistResponse(hls.master(id));
    }

    @GetMapping(value = "/api/videos/{id}/hls/{rendition}/index.m3u8", produces = "application/vnd.apple.mpegurl")
    public ResponseEntity<String> playlist(@PathVariable long id, @PathVariable String rendition) {
        return playlistResponse(hls.playlist(id, rendition));
    }

    @GetMapping(value = "/api/videos/{id}/hls/{rendition}/{segment:[A-Za-z0-9._-]+\\.ts}", produces = "video/mp2t")
    public ResponseEntity<byte[]> segment(@PathVariable long id, @PathVariable String rendition,
            @PathVariable String segment) {
        String key = hls.segmentKey(id, rendition, segment);
        ObjectStorage objects = storage.getIfAvailable();
        if (objects == null) throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("video/mp2t"))
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(objects.read(key));
    }

    private ResponseEntity<String> playlistResponse(String body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma", "no-cache").body(body);
    }
}
