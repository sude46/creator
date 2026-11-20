CREATE TABLE favorite_folder (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    name VARCHAR(30) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_folder_owner FOREIGN KEY (owner_id) REFERENCES user_account(id),
    CONSTRAINT uk_folder_owner_name UNIQUE (owner_id, name)
);
CREATE TABLE favorite_folder_item (
    folder_id BIGINT NOT NULL,
    video_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (folder_id, video_id),
    CONSTRAINT fk_folder_item_folder FOREIGN KEY (folder_id) REFERENCES favorite_folder(id) ON DELETE CASCADE,
    CONSTRAINT fk_folder_item_video FOREIGN KEY (video_id) REFERENCES video(id)
);
CREATE INDEX idx_folder_item_video ON favorite_folder_item(video_id);
CREATE TABLE view_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    video_id BIGINT NOT NULL,
    viewed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_history_user_video UNIQUE (user_id, video_id),
    CONSTRAINT fk_history_user FOREIGN KEY (user_id) REFERENCES user_account(id),
    CONSTRAINT fk_history_video FOREIGN KEY (video_id) REFERENCES video(id)
);
CREATE INDEX idx_history_user_time ON view_history(user_id, viewed_at);
