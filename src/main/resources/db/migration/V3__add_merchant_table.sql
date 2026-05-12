-- V3: Create merchants table
-- Author: bob.wilson
-- Date: 2020-08-10
-- Note: Previously merchant data was managed externally.
-- Moving it here for the monolith (temporary solution... right?)

CREATE TABLE merchants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_code VARCHAR(32) NOT NULL UNIQUE,
    business_name VARCHAR(255) NOT NULL,
    contact_email VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    daily_limit DECIMAL(19,4),
    monthly_limit DECIMAL(19,4),
    webhook_url VARCHAR(500),
    api_key_hash VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX idx_merchant_code ON merchants(merchant_code);
CREATE INDEX idx_merchant_active ON merchants(is_active);

-- Create payment_methods table
CREATE TABLE payment_methods (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    card_last_four VARCHAR(4),
    card_brand VARCHAR(20),
    expiry_month INT,
    expiry_year INT,
    is_default BOOLEAN DEFAULT FALSE,
    token VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pm_customer ON payment_methods(customer_id);

-- Insert some test merchants for development
INSERT INTO merchants (merchant_code, business_name, contact_email, is_active, daily_limit, monthly_limit, webhook_url)
VALUES
    ('MERCH001', 'Test Store Inc', 'admin@teststore.com', TRUE, 100000.00, 2000000.00, 'https://teststore.com/webhooks/payments'),
    ('MERCH002', 'BigShop Ltd', 'payments@bigshop.com', TRUE, 50000.00, 1000000.00, NULL),
    ('MERCH003', 'Inactive Corp', 'old@inactive.com', FALSE, 10000.00, 100000.00, NULL);
