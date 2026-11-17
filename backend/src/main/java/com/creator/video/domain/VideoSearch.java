package com.creator.video.domain;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import java.nio.charset.StandardCharsets;

public record VideoSearch(String title, Long categoryId) {
    public VideoSearch {
        title = title == null ? "" : title.strip();
        if (title.length() > 120 || !StandardCharsets.UTF_8.newEncoder().canEncode(title)
                || title.codePoints().anyMatch(Character::isISOControl) || (categoryId != null && categoryId <= 0)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    public String pattern() { return "%" + title.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"; }
}
