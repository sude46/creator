CREATE TABLE video_category (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(32) NOT NULL,
    name_key VARBINARY(256) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_category_name UNIQUE (name_key),
    CONSTRAINT ck_category_sort CHECK (sort_order BETWEEN 0 AND 10000),
    CONSTRAINT ck_category_version CHECK (version >= 0)
);

CREATE INDEX idx_category_enabled_sort ON video_category(enabled, sort_order, id);

CREATE TABLE operation_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_id BIGINT NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id BIGINT NOT NULL,
    request_id VARCHAR(36),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_operation_actor FOREIGN KEY (actor_id) REFERENCES user_account(id)
);

CREATE INDEX idx_operation_actor_time ON operation_log(actor_id, created_at, id);
