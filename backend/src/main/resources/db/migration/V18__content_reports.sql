CREATE TABLE content_report (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reporter_id BIGINT NOT NULL,
    target_type VARCHAR(16) NOT NULL,
    target_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    resolver_id BIGINT,
    resolution_reason VARCHAR(500),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    resolved_at TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES user_account(id),
    CONSTRAINT fk_report_resolver FOREIGN KEY (resolver_id) REFERENCES user_account(id),
    CONSTRAINT ck_report_target CHECK (target_type IN ('VIDEO', 'COMMENT')),
    CONSTRAINT ck_report_status CHECK (status IN ('OPEN', 'DISMISSED', 'REMOVED'))
);
CREATE INDEX idx_report_status ON content_report(status, id);
