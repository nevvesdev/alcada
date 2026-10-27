ALTER TABLE credit_request ADD COLUMN claimed_until TIMESTAMPTZ;

CREATE TABLE outbox_event (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(500),
    CONSTRAINT ck_outbox_event_attempts CHECK (attempts >= 0)
);

CREATE INDEX ix_outbox_event_pending ON outbox_event (occurred_at) WHERE published_at IS NULL;
CREATE INDEX ix_outbox_event_aggregate ON outbox_event (aggregate_id);