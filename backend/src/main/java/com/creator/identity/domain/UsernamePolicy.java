package com.creator.identity.domain;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import java.util.Locale;

public final class UsernamePolicy {
    private UsernamePolicy() {
    }

    public static String normalize(String username) {
        if (username == null || !username.matches("[a-zA-Z0-9_]{3,32}")) {
            throw new BusinessException(ErrorCode.INVALID_USERNAME);
        }
        return username.toLowerCase(Locale.ROOT);
    }
}
