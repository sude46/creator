package com.creator.video.web;

import com.creator.video.domain.PublicVideo;
import java.time.LocalDateTime;

public record PublicVideoView(String id, String authorId, String authorName, String categoryId, String categoryName,
        String title, String description, long durationMillis, int width, int height, LocalDateTime publishedAt,
        boolean hasCover, String coverKey, long viewCount, long favoriteCount, long likeCount, boolean hasHls, boolean hasSubtitle) {
    public static PublicVideoView from(PublicVideo video) {
        return new PublicVideoView(Long.toString(video.id()), Long.toString(video.ownerId()), video.authorName(),
                Long.toString(video.categoryId()), video.categoryName(), video.title(), video.description(), video.durationMillis(),
                video.mediaWidth(), video.mediaHeight(), video.publishedAt(), video.coverKey() != null, video.coverKey(),
                video.viewCount(), video.favoriteCount(), video.likeCount(), video.hasHls(), video.hasSubtitle());
    }
}
