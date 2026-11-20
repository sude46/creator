CREATE TABLE video_view_daily (
    video_id BIGINT NOT NULL,
    view_date DATE NOT NULL,
    view_count BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (video_id, view_date),
    CONSTRAINT fk_daily_view_video FOREIGN KEY (video_id) REFERENCES video(id)
);
CREATE INDEX idx_daily_view_date ON video_view_daily(view_date, video_id);
