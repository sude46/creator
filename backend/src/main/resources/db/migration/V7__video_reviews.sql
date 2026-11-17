ALTER TABLE video ADD COLUMN review_round BIGINT NOT NULL DEFAULT 0;
ALTER TABLE video ADD COLUMN review_submitted_at TIMESTAMP(6);
ALTER TABLE video ADD COLUMN published_at TIMESTAMP(6);
ALTER TABLE video ADD CONSTRAINT ck_review_round CHECK (review_round >= 0);
CREATE INDEX idx_video_review_queue ON video(review_status, review_submitted_at, id);

CREATE TABLE review_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    video_id BIGINT NOT NULL,
    review_round BIGINT NOT NULL,
    content_version BIGINT NOT NULL,
    processing_generation BIGINT NOT NULL,
    reviewer_id BIGINT NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    title VARCHAR(120) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    category_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_review_video_round UNIQUE (video_id, review_round),
    CONSTRAINT fk_review_video FOREIGN KEY (video_id) REFERENCES video(id),
    CONSTRAINT fk_review_reviewer FOREIGN KEY (reviewer_id) REFERENCES user_account(id),
    CONSTRAINT fk_review_category FOREIGN KEY (category_id) REFERENCES video_category(id),
    CONSTRAINT ck_review_outcome CHECK (outcome IN ('APPROVED', 'REJECTED')),
    CONSTRAINT ck_review_reason CHECK (outcome <> 'REJECTED' OR CHAR_LENGTH(TRIM(reason)) > 0),
    CONSTRAINT ck_review_record_round CHECK (review_round > 0 AND content_version >= 0 AND processing_generation > 0)
);
