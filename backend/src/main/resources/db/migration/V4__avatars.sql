-- Profile photos. Images are small (400x400 JPEG, ~30-60 KB), so they live in PostgreSQL instead of
-- object storage: one less moving part on a single-host deployment.
-- avatar_key changes on every upload and is part of the image URL, so browsers can cache it forever.
ALTER TABLE users ADD COLUMN avatar_key VARCHAR(32);

CREATE TABLE avatars (
    user_id    BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    image      BYTEA NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
