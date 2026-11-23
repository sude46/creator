ALTER TABLE user_account ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE user_session (
    id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    session_hash CHAR(64) NOT NULL,
    session_version BIGINT NOT NULL,
    ip VARCHAR(45),
    user_agent VARCHAR(512),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_seen_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_user_session_hash UNIQUE (session_hash),
    CONSTRAINT fk_user_session_user FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE CASCADE,
    CONSTRAINT ck_user_session_version CHECK (session_version >= 0)
);
CREATE INDEX idx_user_session_active ON user_session(user_id, revoked_at, expires_at);
