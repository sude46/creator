ALTER TABLE processing_task ADD COLUMN dispatch_token VARCHAR(36);
ALTER TABLE processing_task ADD COLUMN dispatch_lease_until TIMESTAMP(6);
ALTER TABLE processing_task ADD COLUMN next_dispatch_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
ALTER TABLE processing_task ADD COLUMN dispatch_attempts BIGINT NOT NULL DEFAULT 0;
ALTER TABLE processing_task ADD COLUMN published_at TIMESTAMP(6);
ALTER TABLE processing_task ADD COLUMN last_dispatch_error VARCHAR(32);
ALTER TABLE processing_task ADD CONSTRAINT ck_dispatch_attempts CHECK (dispatch_attempts >= 0);
ALTER TABLE processing_task ADD CONSTRAINT ck_dispatch_error CHECK (last_dispatch_error IS NULL OR
    last_dispatch_error IN ('UNROUTABLE', 'BROKER_NACK', 'CONFIRM_TIMEOUT', 'BROKER_UNAVAILABLE', 'INTERRUPTED'));
CREATE INDEX idx_task_dispatch ON processing_task(state, next_dispatch_at, id, dispatch_lease_until);
