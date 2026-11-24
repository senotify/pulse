-- Photos attached to posts. Uploaded first (post_id NULL), then claimed when the post is created,
-- like X's media upload flow. Unclaimed uploads are deleted after an hour.
CREATE TABLE media (
    id         BIGINT PRIMARY KEY,                     -- Snowflake id, also the public URL
    owner_id   BIGINT   NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    post_id    BIGINT REFERENCES posts (id) ON DELETE CASCADE,
    position   SMALLINT NOT NULL DEFAULT 0,            -- order within the post, 0-3
    image      BYTEA    NOT NULL,
    width      INTEGER  NOT NULL,
    height     INTEGER  NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX media_post_idx ON media (post_id, position);
CREATE INDEX media_unclaimed_idx ON media (owner_id, post_id, created_at);
