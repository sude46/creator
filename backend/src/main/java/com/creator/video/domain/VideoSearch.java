package com.creator.video.domain;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import java.nio.charset.StandardCharsets;

public record VideoSearch(String title, Long categoryId, String tag) {
    public VideoSearch {
        title = title == null ? "" : title.strip();
        if (title.length() > 120 || !StandardCharsets.UTF_8.newEncoder().canEncode(title)
                || title.codePoints().anyMatch(Character::isISOControl) || (categoryId != null && categoryId <= 0)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        tag = tag == null || tag.isBlank() ? null : VideoTags.key(VideoTags.normalizeName(tag));
    }

    public VideoSearch(String title, Long categoryId) { this(title, categoryId, null); }

    public String pattern() { return "%" + title.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"; }
}
