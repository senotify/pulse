-- In-app notifications, filled from outbox events. One row per notification; likes on the same
-- post are grouped into a single row whose actor_count grows and which moves to the top again
-- (its id is re-issued, ids being time ordered) and becomes unread.
CREATE TABLE notifications (
    id              BIGINT PRIMARY KEY,                -- Snowflake id; the keyset cursor
    user_id         BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE, -- recipient
    type            VARCHAR(10) NOT NULL,              -- LIKE, REPOST, REPLY, FOLLOW, MENTION
    actor_id        BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE, -- latest actor
    actor_count     INTEGER     NOT NULL DEFAULT 1,
    post_id         BIGINT REFERENCES posts (id) ON DELETE CASCADE, -- the subject; null for FOLLOW
    outbox_event_id BIGINT      NOT NULL,              -- dedupes redelivered events
    read_at         TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT notifications_event_uq UNIQUE (outbox_event_id, user_id)
);
CREATE INDEX notifications_user_idx ON notifications (user_id, id);
CREATE INDEX notifications_group_idx ON notifications (user_id, type, post_id);
