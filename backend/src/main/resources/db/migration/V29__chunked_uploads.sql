ALTER TABLE upload_attempt ADD COLUMN declared_size BIGINT;
ALTER TABLE upload_attempt ADD COLUMN chunk_size INT;
ALTER TABLE upload_attempt ADD COLUMN received_bytes BIGINT NOT NULL DEFAULT 0;
ALTER TABLE upload_attempt ADD CONSTRAINT ck_upload_declared_size CHECK (declared_size IS NULL OR declared_size > 0);
ALTER TABLE upload_attempt ADD CONSTRAINT ck_upload_chunk_size CHECK (chunk_size IS NULL OR chunk_size > 0);
ALTER TABLE upload_attempt ADD CONSTRAINT ck_upload_received_bytes CHECK (received_bytes >= 0);

CREATE TABLE upload_chunk (
    attempt_id VARCHAR(36) NOT NULL,
    chunk_index INT NOT NULL,
    size_bytes INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (attempt_id, chunk_index),
    CONSTRAINT fk_chunk_attempt FOREIGN KEY (attempt_id) REFERENCES upload_attempt(id) ON DELETE CASCADE,
    CONSTRAINT ck_chunk_index CHECK (chunk_index >= 0),
    CONSTRAINT ck_chunk_size CHECK (size_bytes > 0)
);

CREATE INDEX idx_upload_owner_video ON upload_attempt(owner_id, video_id, state);
