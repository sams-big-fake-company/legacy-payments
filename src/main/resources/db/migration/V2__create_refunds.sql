-- V2: Create refunds table
-- Author: jane.doe
-- Date: 2020-01-22

CREATE TABLE refunds (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    refund_id VARCHAR(64) NOT NULL UNIQUE,
    payment_id BIGINT NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    reason VARCHAR(500),
    status VARCHAR(20) NOT NULL,
    initiated_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    CONSTRAINT fk_refund_payment FOREIGN KEY (payment_id) REFERENCES payments(id)
);

CREATE INDEX idx_refund_payment ON refunds(payment_id);
CREATE INDEX idx_refund_status ON refunds(status);
