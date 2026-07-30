package com.bigfake.payments.model.enums;

/**
 * Payment lifecycle status.
 *
 * State transitions:
 * PENDING -> PROCESSING -> COMPLETED
 * PENDING -> PROCESSING -> FAILED
 * COMPLETED -> REFUNDED
 *
 * TODO: PAY-3300 - Add PARTIALLY_REFUNDED status
 * TODO: PAY-3301 - Add EXPIRED status for abandoned payments
 */
public enum PaymentStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    REFUNDED;

    /**
     * Check if payment is in a terminal state.
     * @deprecated Use PaymentStatusHelper instead (PAY-3302)
     */
    @Deprecated(since = "1.4.2")
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == REFUNDED;
    }
}
