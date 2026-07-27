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
        PaymentException exception = new PaymentException("Something went wrong");

        assertEquals("Something went wrong", exception.getMessage());
        assertEquals("PAYMENT_ERROR", exception.getErrorCode());
        assertNull(exception.getCause());
    }

    @Test
    void messageAndErrorCodeConstructor_keepsErrorCode() {
        PaymentException exception = new PaymentException("Merchant is inactive", "MERCHANT_INACTIVE");

        assertEquals("Merchant is inactive", exception.getMessage());
        assertEquals("MERCHANT_INACTIVE", exception.getErrorCode());
    }

    @Test
    void causeConstructor_keepsCauseAndDefaultErrorCode() {
        IllegalStateException cause = new IllegalStateException("gateway timeout");

        PaymentException exception = new PaymentException("Gateway call failed", cause);

        assertSame(cause, exception.getCause());
        assertEquals("PAYMENT_ERROR", exception.getErrorCode());
    }

    @Test
    void insufficientFundsException_exposesAmountsAndFixedErrorCode() {
        InsufficientFundsException exception = new InsufficientFundsException(
                "ACH daily limit exceeded", new BigDecimal("1000.00"), new BigDecimal("250.00"));

        assertEquals("ACH daily limit exceeded", exception.getMessage());
        assertEquals("INSUFFICIENT_FUNDS", exception.getErrorCode());
        assertEquals(new BigDecimal("1000.00"), exception.getRequestedAmount());
        assertEquals(new BigDecimal("250.00"), exception.getAvailableAmount());
        assertTrue(exception instanceof PaymentException);
    }
}
