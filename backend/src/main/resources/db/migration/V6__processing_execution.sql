ALTER TABLE video ADD COLUMN processing_generation BIGINT NOT NULL DEFAULT 1;
ALTER TABLE video ADD COLUMN duration_millis BIGINT;
ALTER TABLE video ADD COLUMN media_width INT;
ALTER TABLE video ADD COLUMN media_height INT;
ALTER TABLE video ADD CONSTRAINT ck_video_generation CHECK (processing_generation > 0);
ALTER TABLE video ADD CONSTRAINT ck_video_duration CHECK (duration_millis IS NULL OR duration_millis > 0);
ALTER TABLE media_asset DROP CONSTRAINT ck_asset_kind;
ALTER TABLE media_asset ADD CONSTRAINT ck_asset_kind CHECK (kind IN ('SOURCE', 'COVER'));
ALTER TABLE processing_task ADD COLUMN execution_token VARCHAR(36);
ALTER TABLE processing_task ADD COLUMN execution_lease_until TIMESTAMP(6);
ALTER TABLE processing_task ADD COLUMN execution_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE processing_task ADD COLUMN next_attempt_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
ALTER TABLE processing_task ADD COLUMN failure_code VARCHAR(32);
ALTER TABLE processing_task ADD CONSTRAINT ck_execution_attempts CHECK (execution_attempts >= 0);
CREATE INDEX idx_execution_recovery ON processing_task(state, execution_lease_until, id);
CREATE INDEX idx_execution_retry ON processing_task(state, next_attempt_at, id);

CREATE TABLE processing_output (
    execution_token VARCHAR(36) NOT NULL,
    task_id BIGINT NOT NULL,
    object_key VARCHAR(128) NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'STAGING',
    expires_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (execution_token),
    CONSTRAINT uk_processing_output_key UNIQUE (object_key),
    CONSTRAINT fk_output_task FOREIGN KEY (task_id) REFERENCES processing_task(id) ON DELETE CASCADE,
    CONSTRAINT ck_output_state CHECK (state IN ('STAGING', 'BOUND', 'CLEANING', 'DELETED'))
);
CREATE INDEX idx_output_cleanup ON processing_output(state, expires_at, execution_token);
