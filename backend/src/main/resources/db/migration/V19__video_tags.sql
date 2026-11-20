CREATE TABLE video_tag (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(20) NOT NULL,
    name_key VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_tag_name_key UNIQUE (name_key)
);
CREATE TABLE video_tag_item (
    video_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (video_id, tag_id),
    CONSTRAINT fk_tag_item_video FOREIGN KEY (video_id) REFERENCES video(id),
    CONSTRAINT fk_tag_item_tag FOREIGN KEY (tag_id) REFERENCES video_tag(id)
);
CREATE INDEX idx_tag_item_tag ON video_tag_item(tag_id, video_id);
