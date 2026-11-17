package com.creator.video.domain;

import com.creator.common.*;
import java.nio.charset.StandardCharsets;

public record ModerationReason(String value) {
    public ModerationReason {
        value = value == null ? "" : value.strip();
        if (value.isBlank() || value.length() > 500 || !StandardCharsets.UTF_8.newEncoder().canEncode(value)
                || value.codePoints().anyMatch(character -> Character.isISOControl(character)
                    && character != '\n' && character != '\r' && character != '\t')) {
            throw new BusinessException(ErrorCode.INVALID_MODERATION_REASON);
        }
    }
}
