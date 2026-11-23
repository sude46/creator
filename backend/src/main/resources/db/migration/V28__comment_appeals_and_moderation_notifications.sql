ALTER TABLE content_appeal DROP CONSTRAINT ck_appeal_target;
ALTER TABLE content_appeal ADD CONSTRAINT ck_appeal_target
    CHECK (target_type IN ('VIDEO', 'COMMENT'));
ALTER TABLE notification DROP CONSTRAINT ck_notification_type;
ALTER TABLE notification ADD CONSTRAINT ck_notification_type
    CHECK (type IN ('REVIEW', 'COMMENT', 'FOLLOW', 'REPORT', 'APPEAL', 'MODERATION'));
