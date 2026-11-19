CREATE TABLE notification (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    type VARCHAR(16) NOT NULL,
    title VARCHAR(120) NOT NULL,
    body VARCHAR(500) NOT NULL DEFAULT '',
    link VARCHAR(200),
    read_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES user_account(id),
    CONSTRAINT ck_notification_type CHECK (type IN ('REVIEW', 'COMMENT', 'FOLLOW'))
);
CREATE INDEX idx_notification_user ON notification(user_id, read_at, id);
