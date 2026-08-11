package com.bigfake.payments.exception;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the payment exception hierarchy.
 */
class PaymentExceptionTest {

    @Test
    void messageOnlyConstructor_usesDefaultErrorCode() {
        PaymentException exception = new PaymentException("gateway down");

        assertEquals("gateway down", exception.getMessage());
        assertEquals("PAYMENT_ERROR", exception.getErrorCode());
        assertNull(exception.getCause());
    }

    @Test
    void messageAndCodeConstructor_retainsErrorCode() {
        PaymentException exception = new PaymentException("no such merchant", "MERCHANT_NOT_FOUND");

        assertEquals("no such merchant", exception.getMessage());
        assertEquals("MERCHANT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void causeConstructor_retainsCauseAndDefaultErrorCode() {
        IllegalStateException cause = new IllegalStateException("socket closed");
        PaymentException exception = new PaymentException("gateway failure", cause);

        assertSame(cause, exception.getCause());
        assertEquals("PAYMENT_ERROR", exception.getErrorCode());
    }

    @Test
    void insufficientFundsException_exposesAmountsAndFixedErrorCode() {
        InsufficientFundsException exception = new InsufficientFundsException(
                "Merchant daily limit would be exceeded",
                new BigDecimal("900.00"),
                new BigDecimal("250.00"));

        assertEquals("Merchant daily limit would be exceeded", exception.getMessage());
        assertEquals("INSUFFICIENT_FUNDS", exception.getErrorCode());
        assertEquals(new BigDecimal("900.00"), exception.getRequestedAmount());
        assertEquals(new BigDecimal("250.00"), exception.getAvailableAmount());
        assertTrue(exception instanceof PaymentException);
    }
}
