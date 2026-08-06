package com.bigfake.payments.exception;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the payment exception hierarchy.
 */
class PaymentExceptionTest {

    @Test
    void messageOnlyConstructorUsesDefaultErrorCode() {
        PaymentException exception = new PaymentException("something went wrong");

        assertEquals("something went wrong", exception.getMessage());
        assertEquals("PAYMENT_ERROR", exception.getErrorCode());
    }

    @Test
    void errorCodeConstructorKeepsTheProvidedCode() {
        PaymentException exception = new PaymentException("Merchant is inactive", "MERCHANT_INACTIVE");

        assertEquals("MERCHANT_INACTIVE", exception.getErrorCode());
    }

    @Test
    void causeConstructorKeepsCauseAndDefaultErrorCode() {
        IllegalStateException cause = new IllegalStateException("connection reset");

        PaymentException exception = new PaymentException("gateway unreachable", cause);

        assertSame(cause, exception.getCause());
        assertEquals("PAYMENT_ERROR", exception.getErrorCode());
    }

    @Test
    void insufficientFundsCarriesRequestedAndAvailableAmounts() {
        InsufficientFundsException exception = new InsufficientFundsException(
                "Merchant daily limit would be exceeded", new BigDecimal("99.99"), new BigDecimal("50.00"));

        assertTrue(exception instanceof PaymentException);
        assertEquals("INSUFFICIENT_FUNDS", exception.getErrorCode());
        assertEquals(new BigDecimal("99.99"), exception.getRequestedAmount());
        assertEquals(new BigDecimal("50.00"), exception.getAvailableAmount());
        assertEquals("Merchant daily limit would be exceeded", exception.getMessage());
    }
}
