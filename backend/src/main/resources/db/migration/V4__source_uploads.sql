CREATE TABLE upload_attempt (
    id VARCHAR(36) NOT NULL,
    video_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    object_key VARCHAR(128) NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'UPLOADING',
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_upload_object UNIQUE (object_key),
    CONSTRAINT fk_upload_video FOREIGN KEY (video_id) REFERENCES video(id),
    CONSTRAINT fk_upload_owner FOREIGN KEY (owner_id) REFERENCES user_account(id),
    CONSTRAINT ck_upload_state CHECK (state IN ('UPLOADING', 'BOUND', 'CLEANING', 'DELETED'))
);
CREATE INDEX idx_upload_expiry ON upload_attempt(state, expires_at, id);
CREATE INDEX idx_upload_owner ON upload_attempt(owner_id, state);

CREATE TABLE media_asset (
    id BIGINT NOT NULL AUTO_INCREMENT,
    video_id BIGINT NOT NULL,
    kind VARCHAR(16) NOT NULL,
    object_key VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_asset_video_kind UNIQUE (video_id, kind),
    CONSTRAINT uk_asset_object UNIQUE (object_key),
    CONSTRAINT fk_asset_video FOREIGN KEY (video_id) REFERENCES video(id),
    CONSTRAINT ck_asset_kind CHECK (kind IN ('SOURCE')),
    CONSTRAINT ck_asset_size CHECK (size_bytes > 0)
);
ALTER TABLE video ADD COLUMN source_asset_id BIGINT;
ALTER TABLE video ADD CONSTRAINT fk_video_source FOREIGN KEY (source_asset_id) REFERENCES media_asset(id);
ALTER TABLE video ADD CONSTRAINT uk_video_source UNIQUE (source_asset_id);

CREATE TABLE processing_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    video_id BIGINT NOT NULL,
    generation BIGINT NOT NULL DEFAULT 1,
    state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_task_generation UNIQUE (video_id, generation),
    CONSTRAINT fk_task_video FOREIGN KEY (video_id) REFERENCES video(id),
    CONSTRAINT ck_task_generation CHECK (generation > 0),
    CONSTRAINT ck_task_state CHECK (state IN ('PENDING', 'RUNNING', 'RETRY_WAIT', 'SUCCEEDED', 'FAILED', 'CANCELLED'))
);
