CREATE TABLE IF NOT EXISTS transactions (
    id UUID PRIMARY KEY,
    to_address VARCHAR(42) NOT NULL,
    amount_ether NUMERIC(38, 18) NOT NULL,
    status VARCHAR(32) NOT NULL,
    transaction_hash VARCHAR(66),
    error_message TEXT,
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT
);

CREATE INDEX IF NOT EXISTS idx_transactions_status
    ON transactions(status);

CREATE UNIQUE INDEX IF NOT EXISTS ux_transactions_hash
    ON transactions(transaction_hash);
