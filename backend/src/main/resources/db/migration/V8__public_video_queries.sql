CREATE INDEX idx_video_publication ON video(publication_status, published_at DESC, id DESC);
