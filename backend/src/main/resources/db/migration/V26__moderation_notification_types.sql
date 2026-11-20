ALTER TABLE notification DROP CONSTRAINT ck_notification_type;
ALTER TABLE notification ADD CONSTRAINT ck_notification_type
    CHECK (type IN ('REVIEW', 'COMMENT', 'FOLLOW', 'REPORT', 'APPEAL'));
