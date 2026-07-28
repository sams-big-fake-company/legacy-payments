package com.bigfake.payments.exception;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Unit tests for the payment exception hierarchy.
 */
class PaymentExceptionTest {

    @Test
    void messageOnlyConstructorUsesDefaultErrorCode() {
        PaymentException exception = new PaymentException("boom");

        assertEquals("boom", exception.getMessage());
        assertEquals("PAYMENT_ERROR", exception.getErrorCode());
        assertNull(exception.getCause());
    }

    @Test
    void errorCodeConstructorKeepsSuppliedCode() {
        PaymentException exception = new PaymentException("not found", "PAYMENT_NOT_FOUND");

        assertEquals("not found", exception.getMessage());
        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void causeConstructorKeepsCauseAndDefaultErrorCode() {
        IllegalStateException cause = new IllegalStateException("root cause");

        PaymentException exception = new PaymentException("wrapped", cause);

        assertEquals("wrapped", exception.getMessage());
        assertEquals("PAYMENT_ERROR", exception.getErrorCode());
        assertSame(cause, exception.getCause());
    }

    @Test
    void insufficientFundsExceptionCarriesAmountsAndFixedErrorCode() {
        InsufficientFundsException exception = new InsufficientFundsException(
                "limit exceeded", new BigDecimal("150.00"), new BigDecimal("40.00"));

        assertEquals("limit exceeded", exception.getMessage());
        assertEquals("INSUFFICIENT_FUNDS", exception.getErrorCode());
        assertEquals(new BigDecimal("150.00"), exception.getRequestedAmount());
        assertEquals(new BigDecimal("40.00"), exception.getAvailableAmount());
    }
}
