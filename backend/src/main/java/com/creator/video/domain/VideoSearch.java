package com.creator.video.domain;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import java.nio.charset.StandardCharsets;

public record VideoSearch(String title, Long categoryId, String tag, String author) {
    public VideoSearch {
        title = title == null ? "" : title.strip();
        author = author == null ? "" : author.strip();
        if (title.length() > 120 || author.length() > 32 || !StandardCharsets.UTF_8.newEncoder().canEncode(title)
                || !StandardCharsets.UTF_8.newEncoder().canEncode(author)
                || title.codePoints().anyMatch(Character::isISOControl)
                || author.codePoints().anyMatch(Character::isISOControl)
                || (categoryId != null && categoryId <= 0)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        tag = tag == null || tag.isBlank() ? null : VideoTags.key(VideoTags.normalizeName(tag));
    }

    public VideoSearch(String title, Long categoryId, String tag) { this(title, categoryId, tag, null); }
    public VideoSearch(String title, Long categoryId) { this(title, categoryId, null, null); }

    public String pattern() { return "%" + escape(title) + "%"; }
    public String authorPattern() { return author.isEmpty() ? null : "%" + escape(author) + "%"; }
    private static String escape(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
