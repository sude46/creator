CREATE TABLE user_profile (
    user_id BIGINT NOT NULL,
    display_name VARCHAR(32),
    bio VARCHAR(200),
    website VARCHAR(200),
    avatar MEDIUMBLOB,
    avatar_content_type VARCHAR(32),
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id),
    CONSTRAINT fk_profile_user FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE CASCADE
);
