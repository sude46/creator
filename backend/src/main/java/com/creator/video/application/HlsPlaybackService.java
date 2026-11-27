package com.creator.video.application;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.video.domain.HlsPlaylist;
import com.creator.video.domain.VideoQueryRepository;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class HlsPlaybackService {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9]{1,16}");
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9._-]+\\.ts");
    private final VideoQueryRepository videos;

    public HlsPlaybackService(VideoQueryRepository videos) {
        this.videos = videos;
    }

    public String master(long videoId) {
        videos.findPublic(videoId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        var renditions = renditions(videoId);
        var result = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-INDEPENDENT-SEGMENTS\n");
        for (var rendition : renditions) {
            result.append("#EXT-X-STREAM-INF:BANDWIDTH=").append(rendition.bandwidth() * 1000L)
                    .append(",RESOLUTION=").append(rendition.width()).append('x').append(rendition.height())
                    .append('\n').append("/api/videos/").append(videoId).append("/hls/")
                    .append(rendition.name()).append("/index.m3u8\n");
        }
        return result.toString();
    }

    public String playlist(long videoId, String rendition) {
        var playlist = rendition(videoId, rendition);
        var result = new StringBuilder();
        for (String line : playlist.playlist().split("\\R", -1)) {
            String value = line.trim();
            if (value.isEmpty()) continue;
            if (value.startsWith("#")) { result.append(value).append('\n'); continue; }
            if (!SEGMENT.matcher(value).matches()) throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
            result.append("/api/videos/").append(videoId).append("/hls/")
                    .append(playlist.name()).append('/').append(value).append('\n');
        }
        return result.toString();
    }

    public String segmentKey(long videoId, String rendition, String segment) {
        if (segment == null || !SEGMENT.matcher(segment).matches()) throw new BusinessException(ErrorCode.NOT_FOUND);
        return rendition(videoId, rendition).baseKey() + "/" + segment;
    }

    private HlsPlaylist rendition(long videoId, String rendition) {
        if (rendition == null || !NAME.matcher(rendition).matches()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        videos.findPublic(videoId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return renditions(videoId).stream().filter(item -> item.name().equals(rendition)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private List<HlsPlaylist> renditions(long videoId) {
        var renditions = videos.hls(videoId);
        if (renditions.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND);
        return renditions;
    }
}
