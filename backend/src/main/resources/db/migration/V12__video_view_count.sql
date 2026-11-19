ALTER TABLE video ADD COLUMN view_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE video ADD CONSTRAINT ck_video_view_count CHECK (view_count >= 0);
