-- V1: Create payments table
-- Author: john.smith
-- Date: 2019-03-15

CREATE TABLE payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id VARCHAR(64) NOT NULL UNIQUE,
    merchant_id BIGINT NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    payment_type VARCHAR(20) NOT NULL,
    description VARCHAR(500),
    customer_email VARCHAR(255),
    customer_name VARCHAR(255),
    card_last_four VARCHAR(4),
    fee_amount DECIMAL(19,4),
    net_amount DECIMAL(19,4),
    failure_reason VARCHAR(1000),
    gateway_reference VARCHAR(128),
    idempotency_key VARCHAR(64) UNIQUE,
    metadata TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    completed_at TIMESTAMP
);

-- Indexes for common queries
CREATE INDEX idx_payment_merchant ON payments(merchant_id);
CREATE INDEX idx_payment_status ON payments(status);
CREATE INDEX idx_payment_created ON payments(created_at);
CREATE INDEX idx_payment_merchant_status ON payments(merchant_id, status);
