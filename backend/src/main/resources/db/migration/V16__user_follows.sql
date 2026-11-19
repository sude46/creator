CREATE TABLE user_follow (
    id BIGINT NOT NULL AUTO_INCREMENT,
    follower_id BIGINT NOT NULL,
    followee_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_follow_pair UNIQUE (follower_id, followee_id),
    CONSTRAINT fk_follow_follower FOREIGN KEY (follower_id) REFERENCES user_account(id),
    CONSTRAINT fk_follow_followee FOREIGN KEY (followee_id) REFERENCES user_account(id),
    CONSTRAINT ck_follow_self CHECK (follower_id <> followee_id)
);
CREATE INDEX idx_follow_followee ON user_follow(followee_id);
