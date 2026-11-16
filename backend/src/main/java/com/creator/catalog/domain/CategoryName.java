package com.creator.catalog.domain;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import java.text.Normalizer;
import java.util.Locale;

public record CategoryName(String value) {
    public CategoryName {
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_CATEGORY);
        }
        value = Normalizer.normalize(value, Normalizer.Form.NFKC).strip();
        if (value.isEmpty() || value.length() > 32 || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new BusinessException(ErrorCode.INVALID_CATEGORY);
        }
    }

    public String canonicalKey() {
        return value.toLowerCase(Locale.ROOT);
    }
}
