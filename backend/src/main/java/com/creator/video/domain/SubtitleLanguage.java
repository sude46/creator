package com.creator.video.domain;

import java.util.Arrays;
import java.util.Optional;

public enum SubtitleLanguage {
    ZH_CN("zh-CN", "中文"),
    ZH_TW("zh-TW", "繁體中文"),
    EN("en", "English"),
    JA("ja", "日本語"),
    KO("ko", "한국어");

    private final String code;
    private final String label;
    SubtitleLanguage(String code, String label) { this.code = code; this.label = label; }
    public String code() { return code; }
    public String label() { return label; }

    public static Optional<SubtitleLanguage> parse(String code) {
        if (code == null) return Optional.empty();
        return Arrays.stream(values()).filter(value -> value.code.equalsIgnoreCase(code)).findFirst();
    }
}
