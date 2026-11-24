-- Relay bookkeeping: failed deliveries back off exponentially and are dead-lettered
-- (published_at set, last_error kept) after the maximum number of attempts.
ALTER TABLE outbox_events ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE outbox_events ADD COLUMN next_attempt_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE outbox_events ADD COLUMN last_error VARCHAR(500);
