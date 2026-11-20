CREATE TABLE content_appeal (
    id BIGINT NOT NULL AUTO_INCREMENT,
    appellant_id BIGINT NOT NULL,
    target_type VARCHAR(16) NOT NULL,
    target_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    resolver_id BIGINT,
    resolution_reason VARCHAR(500),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    resolved_at TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_appeal_appellant FOREIGN KEY (appellant_id) REFERENCES user_account(id),
    CONSTRAINT fk_appeal_resolver FOREIGN KEY (resolver_id) REFERENCES user_account(id),
    CONSTRAINT ck_appeal_target CHECK (target_type IN ('VIDEO')),
    CONSTRAINT ck_appeal_status CHECK (status IN ('OPEN', 'ACCEPTED', 'REJECTED'))
);
CREATE INDEX idx_appeal_status ON content_appeal(status, id);
CREATE INDEX idx_appeal_target ON content_appeal(appellant_id, target_type, target_id, status);
