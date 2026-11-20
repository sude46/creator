package com.creator.video.domain;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public record VideoMetadata(String title, String description, long categoryId, List<String> tags) {
    public VideoMetadata {
        if (title == null || description == null) throw new BusinessException(ErrorCode.INVALID_VIDEO);
        title = title.strip();
        description = description.strip();
        tags = VideoTags.normalize(tags);
        if (title.isEmpty() || title.length() > 120 || description.length() > 2000 || categoryId <= 0
                || !StandardCharsets.UTF_8.newEncoder().canEncode(title)
                || !StandardCharsets.UTF_8.newEncoder().canEncode(description)
                || title.codePoints().anyMatch(Character::isISOControl)
                || description.codePoints().anyMatch(character -> Character.isISOControl(character)
                        && character != '\n' && character != '\r' && character != '\t')) {
            throw new BusinessException(ErrorCode.INVALID_VIDEO);
        }
    }

    public VideoMetadata(String title, String description, long categoryId) {
        this(title, description, categoryId, List.of());
    }

    public byte[] fingerprint() {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var fields = new java.util.ArrayList<String>();
            fields.add(title);
            fields.add(description);
            fields.add(Long.toString(categoryId));
            for (String field : fields) {
                byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            byte[] tagBytes = String.join("\u001f", tags).getBytes(StandardCharsets.UTF_8);
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(tagBytes.length).array());
            digest.update(tagBytes);
            return digest.digest();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
