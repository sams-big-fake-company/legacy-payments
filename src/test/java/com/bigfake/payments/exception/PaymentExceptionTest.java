package com.bigfake.payments.exception;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests for the error codes carried by the payment exception hierarchy.
 */
class PaymentExceptionTest {

    @Test
    void messageOnlyConstructorUsesTheGenericErrorCode() {
        PaymentException exception = new PaymentException("something went wrong");

        assertEquals("something went wrong", exception.getMessage());
        assertEquals("PAYMENT_ERROR", exception.getErrorCode());
    }

    @Test
    void errorCodeConstructorKeepsTheProvidedCode() {
        PaymentException exception = new PaymentException("merchant is inactive", "MERCHANT_INACTIVE");

        assertEquals("MERCHANT_INACTIVE", exception.getErrorCode());
    }

    @Test
    void causeConstructorKeepsTheCauseAndUsesTheGenericErrorCode() {
        IllegalStateException cause = new IllegalStateException("gateway timeout");

        PaymentException exception = new PaymentException("gateway call failed", cause);

        assertSame(cause, exception.getCause());
        assertEquals("PAYMENT_ERROR", exception.getErrorCode());
    }

    @Test
    void insufficientFundsCarriesRequestedAndAvailableAmounts() {
        InsufficientFundsException exception = new InsufficientFundsException(
                "daily limit exceeded", new BigDecimal("120.00"), new BigDecimal("35.50"));

        assertEquals("INSUFFICIENT_FUNDS", exception.getErrorCode());
        assertEquals(new BigDecimal("120.00"), exception.getRequestedAmount());
        assertEquals(new BigDecimal("35.50"), exception.getAvailableAmount());
        assertEquals("daily limit exceeded", exception.getMessage());
    }
}
