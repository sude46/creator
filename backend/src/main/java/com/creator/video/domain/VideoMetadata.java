package com.creator.video.domain;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public record VideoMetadata(String title, String description, long categoryId) {
    public VideoMetadata {
        if (title == null || description == null) throw new BusinessException(ErrorCode.INVALID_VIDEO);
        title = title.strip();
        description = description.strip();
        if (title.isEmpty() || title.length() > 120 || description.length() > 2000 || categoryId <= 0
                || !StandardCharsets.UTF_8.newEncoder().canEncode(title)
                || !StandardCharsets.UTF_8.newEncoder().canEncode(description)
                || title.codePoints().anyMatch(Character::isISOControl)
                || description.codePoints().anyMatch(character -> Character.isISOControl(character)
                        && character != '\n' && character != '\r' && character != '\t')) {
            throw new BusinessException(ErrorCode.INVALID_VIDEO);
        }
    }

    public byte[] fingerprint() {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (String field : new String[] {title, description, Long.toString(categoryId)}) {
                byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
