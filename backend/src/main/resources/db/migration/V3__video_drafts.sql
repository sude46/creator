CREATE TABLE video (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    client_request_id VARCHAR(36) NOT NULL,
    creation_fingerprint VARBINARY(32) NOT NULL,
    processing_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    review_status VARCHAR(16) NOT NULL DEFAULT 'NOT_SUBMITTED',
    publication_status VARCHAR(16) NOT NULL DEFAULT 'UNPUBLISHED',
    moderation_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_video_owner_request UNIQUE (owner_id, client_request_id),
    CONSTRAINT fk_video_owner FOREIGN KEY (owner_id) REFERENCES user_account(id),
    CONSTRAINT fk_video_category FOREIGN KEY (category_id) REFERENCES video_category(id),
    CONSTRAINT ck_video_processing CHECK (processing_status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED')),
    CONSTRAINT ck_video_review CHECK (review_status IN ('NOT_SUBMITTED', 'PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_video_publication CHECK (publication_status IN ('UNPUBLISHED', 'PUBLISHED', 'OFFLINE')),
    CONSTRAINT ck_video_public CHECK (publication_status <> 'PUBLISHED' OR
        (processing_status = 'READY' AND review_status = 'APPROVED' AND moderation_blocked = FALSE AND deleted_at IS NULL)),
    CONSTRAINT ck_video_version CHECK (version >= 0)
);

CREATE INDEX idx_video_owner_id ON video(owner_id, id);
