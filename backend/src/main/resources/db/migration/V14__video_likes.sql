CREATE TABLE video_like (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    video_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_like_user_video UNIQUE (user_id, video_id),
    CONSTRAINT fk_like_user FOREIGN KEY (user_id) REFERENCES user_account(id),
    CONSTRAINT fk_like_video FOREIGN KEY (video_id) REFERENCES video(id)
);
CREATE INDEX idx_like_video ON video_like(video_id);
