CREATE TABLE hls_backfill_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    video_id BIGINT NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    execution_token VARCHAR(36),
    lease_until TIMESTAMP(6),
    next_attempt_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_error VARCHAR(64),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_hls_backfill_video UNIQUE (video_id),
    CONSTRAINT fk_hls_backfill_video FOREIGN KEY (video_id) REFERENCES video(id) ON DELETE CASCADE,
    CONSTRAINT ck_hls_backfill_state CHECK (state IN ('PENDING', 'RUNNING', 'RETRY_WAIT', 'DONE', 'FAILED')),
    CONSTRAINT ck_hls_backfill_attempts CHECK (attempts >= 0)
);
CREATE INDEX idx_hls_backfill_due ON hls_backfill_task(state, next_attempt_at, id);
CREATE INDEX idx_hls_backfill_lease ON hls_backfill_task(state, lease_until, id);
