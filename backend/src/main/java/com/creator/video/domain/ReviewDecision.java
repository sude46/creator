package com.creator.video.domain;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import java.nio.charset.StandardCharsets;

public record ReviewDecision(Outcome outcome, String reason) {
    public enum Outcome { APPROVED, REJECTED }
    public ReviewDecision {
        reason = reason == null ? "" : reason.strip();
        if (outcome == null || reason.length() > 500 || !StandardCharsets.UTF_8.newEncoder().canEncode(reason)
                || reason.codePoints().anyMatch(character -> Character.isISOControl(character)
                    && character != '\n' && character != '\r' && character != '\t')
                || (outcome == Outcome.REJECTED && reason.isBlank())) {
            throw new BusinessException(ErrorCode.INVALID_REVIEW_REASON);
        }
    }
}
