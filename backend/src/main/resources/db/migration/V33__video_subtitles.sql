CREATE TABLE video_subtitle (
    video_id BIGINT NOT NULL,
    content MEDIUMBLOB NOT NULL,
    size_bytes INT NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (video_id),
    CONSTRAINT fk_subtitle_video FOREIGN KEY (video_id) REFERENCES video(id) ON DELETE CASCADE,
    CONSTRAINT ck_subtitle_size CHECK (size_bytes > 0 AND size_bytes <= 524288)
);
