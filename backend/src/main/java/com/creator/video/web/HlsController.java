package com.creator.video.web;

import com.creator.video.application.HlsPlaybackService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HlsController {
    private final HlsPlaybackService hls;
    public HlsController(HlsPlaybackService hls) { this.hls = hls; }

    @GetMapping(value = "/api/videos/{id}/hls/master.m3u8", produces = "application/vnd.apple.mpegurl")
    public ResponseEntity<String> master(@PathVariable long id) {
        return playlistResponse(hls.master(id));
    }

    @GetMapping(value = "/api/videos/{id}/hls/{rendition}/index.m3u8", produces = "application/vnd.apple.mpegurl")
    public ResponseEntity<String> playlist(@PathVariable long id, @PathVariable String rendition) {
        return playlistResponse(hls.playlist(id, rendition));
    }

    private ResponseEntity<String> playlistResponse(String body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma", "no-cache").body(body);
    }
}
