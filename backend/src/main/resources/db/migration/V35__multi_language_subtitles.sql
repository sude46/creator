CREATE TABLE video_subtitle_v2 (
    video_id BIGINT NOT NULL,
    language VARCHAR(16) NOT NULL,
    label VARCHAR(32) NOT NULL,
    content MEDIUMBLOB NOT NULL,
    size_bytes INT NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (video_id, language),
    CONSTRAINT fk_subtitle_v2_video FOREIGN KEY (video_id) REFERENCES video(id) ON DELETE CASCADE,
    CONSTRAINT ck_subtitle_v2_language CHECK (language IN ('zh-CN', 'zh-TW', 'en', 'ja', 'ko')),
    CONSTRAINT ck_subtitle_v2_size CHECK (size_bytes > 0 AND size_bytes <= 524288)
);

INSERT INTO video_subtitle_v2 (video_id, language, label, content, size_bytes, updated_at)
SELECT video_id, 'zh-CN', '中文', content, size_bytes, updated_at FROM video_subtitle;

DROP TABLE video_subtitle;
ALTER TABLE video_subtitle_v2 RENAME TO video_subtitle;
