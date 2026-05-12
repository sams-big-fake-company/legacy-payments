package com.bigfake.payments.exception;

import java.math.BigDecimal;

/**
 * Thrown when a payment cannot be processed due to insufficient funds
 * or exceeding merchant limits.
 */
public class InsufficientFundsException extends PaymentException {

    private final BigDecimal requestedAmount;
    private final BigDecimal availableAmount;

    public InsufficientFundsException(String message, BigDecimal requestedAmount, BigDecimal availableAmount) {
        super(message, "INSUFFICIENT_FUNDS");
        this.requestedAmount = requestedAmount;
        this.availableAmount = availableAmount;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    public BigDecimal getAvailableAmount() {
        return availableAmount;
    }
}
