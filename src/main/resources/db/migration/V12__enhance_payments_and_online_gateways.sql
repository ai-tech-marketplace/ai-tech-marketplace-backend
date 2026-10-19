-- V12__enhance_payments_and_online_gateways.sql
-- Enhance payments table with gateway provider details, transaction tracking, and expiration timestamps

ALTER TABLE payments ADD COLUMN IF NOT EXISTS provider VARCHAR(50);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS provider_transaction_id VARCHAR(100);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS provider_response TEXT;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS paid_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP WITH TIME ZONE;

-- Unique transaction reference index for non-null values
CREATE UNIQUE INDEX IF NOT EXISTS idx_payments_transaction_reference
    ON payments (transaction_reference)
    WHERE transaction_reference IS NOT NULL;

-- Index for querying by gateway's external transaction id
CREATE INDEX IF NOT EXISTS idx_payments_provider_txn
    ON payments (provider_transaction_id);

-- Index for 15-minute unpaid order expiration background scheduler
CREATE INDEX IF NOT EXISTS idx_orders_pending_online_payment
    ON orders (status, payment_status, created_at)
    WHERE status = 'PENDING';
