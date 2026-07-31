package com.bigfake.payments.exception;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Unit tests for the payment exception hierarchy.
 */
class PaymentExceptionTest {

    @Test
    void messageOnlyConstructor_usesDefaultErrorCode() {
        PaymentException exception = new PaymentException("boom");

        assertEquals("boom", exception.getMessage());
        assertEquals("PAYMENT_ERROR", exception.getErrorCode());
    }

    @Test
    void errorCodeConstructor_keepsProvidedCode() {
        PaymentException exception = new PaymentException("no merchant", "MERCHANT_NOT_FOUND");

        assertEquals("MERCHANT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void causeConstructor_keepsCauseAndDefaultErrorCode() {
        IllegalStateException cause = new IllegalStateException("root cause");

        PaymentException exception = new PaymentException("wrapped", cause);

        assertSame(cause, exception.getCause());
        assertEquals("PAYMENT_ERROR", exception.getErrorCode());
    }

    @Test
    void insufficientFunds_exposesRequestedAndAvailableAmounts() {
        InsufficientFundsException exception = new InsufficientFundsException(
                "ACH daily limit exceeded", new BigDecimal("300.00"), new BigDecimal("120.50"));

        assertEquals("INSUFFICIENT_FUNDS", exception.getErrorCode());
        assertEquals(new BigDecimal("300.00"), exception.getRequestedAmount());
        assertEquals(new BigDecimal("120.50"), exception.getAvailableAmount());
        assertEquals("ACH daily limit exceeded", exception.getMessage());
    }
}
