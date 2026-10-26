CREATE TABLE credit_request (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL,
    cpf VARCHAR(11) NOT NULL,
    requested_amount NUMERIC(19, 2) NOT NULL,
    declared_income NUMERIC(19, 2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    credit_score INTEGER,
    risk_verdict VARCHAR(32),
    risk_rejection_reason VARCHAR(64),
    rejection_reason VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_credit_request_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_credit_request_status CHECK (
        status IN ('PENDING', 'BUREAU_CHECKED', 'RISK_ANALYZED', 'UNDER_REVIEW',
                'LIMIT_RESERVED', 'APPROVED', 'REJECTED')
    ),
    CONSTRAINT ck_credit_request_amounts CHECK (requested_amount > 0 AND declared_income >= 0),
    CONSTRAINT ck_credit_request_score CHECK (credit_score IS NULL OR credit_score BETWEEN 0 AND 1000),
    CONSTRAINT ck_credit_request_updated_at CHECK (updated_at >= created_at)
);

CREATE INDEX ix_credit_request_status_updated_at ON credit_request (status, updated_at);