CREATE TABLE video_deletion (
    id BIGINT NOT NULL AUTO_INCREMENT,
    video_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    reason VARCHAR(500),
    state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_error VARCHAR(64),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_video_deletion_video UNIQUE (video_id),
    CONSTRAINT fk_video_deletion_video FOREIGN KEY (video_id) REFERENCES video(id),
    CONSTRAINT fk_video_deletion_owner FOREIGN KEY (owner_id) REFERENCES user_account(id),
    CONSTRAINT ck_video_deletion_state CHECK (state IN ('PENDING', 'CLEANING', 'DONE')),
    CONSTRAINT ck_video_deletion_attempts CHECK (attempts >= 0)
);
CREATE INDEX idx_video_deletion_due ON video_deletion(state, next_attempt_at, id);
