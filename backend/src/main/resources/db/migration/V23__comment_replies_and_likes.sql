ALTER TABLE video_comment ADD COLUMN parent_id BIGINT;
ALTER TABLE video_comment ADD CONSTRAINT fk_comment_parent FOREIGN KEY (parent_id) REFERENCES video_comment(id);
CREATE INDEX idx_comment_parent ON video_comment(parent_id, status, id);
CREATE TABLE video_comment_like (
    id BIGINT NOT NULL AUTO_INCREMENT,
    comment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_comment_like_user UNIQUE (comment_id, user_id),
    CONSTRAINT fk_comment_like_comment FOREIGN KEY (comment_id) REFERENCES video_comment(id),
    CONSTRAINT fk_comment_like_user FOREIGN KEY (user_id) REFERENCES user_account(id)
);
CREATE INDEX idx_comment_like_user ON video_comment_like(user_id, comment_id);
