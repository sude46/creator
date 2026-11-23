CREATE TABLE video_hls (
    video_id BIGINT NOT NULL,
    rendition VARCHAR(16) NOT NULL,
    bandwidth INT NOT NULL,
    width INT NOT NULL,
    height INT NOT NULL,
    base_key VARCHAR(180) NOT NULL,
    playlist_key VARCHAR(180) NOT NULL,
    playlist TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (video_id, rendition),
    CONSTRAINT fk_hls_video FOREIGN KEY (video_id) REFERENCES video(id) ON DELETE CASCADE,
    CONSTRAINT ck_hls_bandwidth CHECK (bandwidth > 0),
    CONSTRAINT ck_hls_dimensions CHECK (width > 0 AND height > 0)
);

CREATE TABLE video_hls_segment (
    video_id BIGINT NOT NULL,
    object_key VARCHAR(180) NOT NULL,
    PRIMARY KEY (video_id, object_key),
    CONSTRAINT fk_hls_segment_video FOREIGN KEY (video_id) REFERENCES video(id) ON DELETE CASCADE
);
