CREATE TABLE video_comment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    video_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content VARCHAR(500) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_comment_video FOREIGN KEY (video_id) REFERENCES video(id),
    CONSTRAINT fk_comment_user FOREIGN KEY (user_id) REFERENCES user_account(id),
    CONSTRAINT ck_comment_status CHECK (status IN ('ACTIVE', 'DELETED'))
);
CREATE INDEX idx_comment_video ON video_comment(video_id, status, id);
