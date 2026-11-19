CREATE TABLE video_collection (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    title VARCHAR(80) NOT NULL,
    description VARCHAR(500) NOT NULL DEFAULT '',
    visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_collection_owner FOREIGN KEY (owner_id) REFERENCES user_account(id),
    CONSTRAINT ck_collection_visibility CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
    CONSTRAINT ck_collection_version CHECK (version >= 0)
);
CREATE INDEX idx_collection_owner ON video_collection(owner_id, id);

CREATE TABLE video_collection_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    collection_id BIGINT NOT NULL,
    video_id BIGINT NOT NULL,
    sort_order INT NOT NULL,
    added_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_collection_video UNIQUE (collection_id, video_id),
    CONSTRAINT fk_item_collection FOREIGN KEY (collection_id) REFERENCES video_collection(id),
    CONSTRAINT fk_item_video FOREIGN KEY (video_id) REFERENCES video(id),
    CONSTRAINT ck_item_order CHECK (sort_order >= 0)
);
CREATE INDEX idx_collection_item_order ON video_collection_item(collection_id, sort_order, id);
