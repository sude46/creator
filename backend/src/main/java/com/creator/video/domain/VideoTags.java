package com.creator.video.domain;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public final class VideoTags {
    public static final int MAX_TAGS = 5;
    public static final int MAX_LENGTH = 20;

    private VideoTags() { }

    public static List<String> normalize(List<String> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        if (raw.size() > MAX_TAGS) throw new BusinessException(ErrorCode.INVALID_TAG);
        var result = new ArrayList<String>();
        var keys = new HashSet<String>();
        for (String value : raw) {
            String name = normalizeName(value);
            if (!keys.add(key(name))) throw new BusinessException(ErrorCode.INVALID_TAG);
            result.add(name);
        }
        return List.copyOf(result);
    }

    public static String normalizeName(String value) {
        if (value == null) throw new BusinessException(ErrorCode.INVALID_TAG);
        String name = value.strip();
        while (name.startsWith("#")) name = name.substring(1).strip();
        name = name.replaceAll("\\s+", " ");
        if (name.isEmpty() || name.codePointCount(0, name.length()) > MAX_LENGTH
                || name.indexOf(',') >= 0 || name.indexOf('，') >= 0 || name.indexOf('#') >= 0
                || !StandardCharsets.UTF_8.newEncoder().canEncode(name)
                || name.codePoints().anyMatch(Character::isISOControl)) {
            throw new BusinessException(ErrorCode.INVALID_TAG);
        }
        return name;
    }

    public static String key(String name) { return name.toLowerCase(Locale.ROOT); }
}
