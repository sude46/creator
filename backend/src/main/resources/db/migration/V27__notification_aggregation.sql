ALTER TABLE notification ADD COLUMN aggregate_key VARCHAR(200);
ALTER TABLE notification ADD COLUMN aggregate_count INT NOT NULL DEFAULT 1;
ALTER TABLE notification ADD COLUMN updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
UPDATE notification SET updated_at = created_at;
CREATE INDEX idx_notification_aggregate ON notification(user_id, type, aggregate_key, read_at, updated_at);
