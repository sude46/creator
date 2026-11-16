package com.creator.identity.domain;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (!withinEncodingLimits(password) || password.length() < 10
                || password.codePoints().noneMatch(Character::isLetter)
                || password.codePoints().noneMatch(Character::isDigit)) {
            throw new BusinessException(ErrorCode.WEAK_PASSWORD);
        }
    }

    public static boolean withinEncodingLimits(String password) {
        return password != null && !password.isEmpty() && password.length() <= 64
                && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
