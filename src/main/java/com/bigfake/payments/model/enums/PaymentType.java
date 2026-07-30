package com.bigfake.payments.model.enums;

/**
 * Supported payment method types.
 *
 * TODO: PAY-4300 - Add CRYPTO, BNPL (buy now pay later) types
 */
public enum PaymentType {
    CREDIT_CARD,
    DEBIT,
    WIRE,
    ACH;

    /**
     * Returns the processing fee percentage for this payment type.
     * @deprecated Fee calculation should be in FeeService (PAY-2800)
     */
    @Deprecated(since = "1.4.2")
    public double getFeePercentage() {
        switch (this) {
            case CREDIT_CARD:
                return 0.029; // 2.9%
            case DEBIT:
                return 0.015; // 1.5%
            case WIRE:
                return 0.001; // 0.1%
            case ACH:
                return 0.008; // 0.8%
            default:
                return 0.03; // default 3%
        }
    }
}
