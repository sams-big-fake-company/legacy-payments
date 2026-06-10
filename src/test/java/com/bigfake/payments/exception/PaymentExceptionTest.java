package com.bigfake.payments.exception;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PaymentException and InsufficientFundsException.
 */
class PaymentExceptionTest {

    @Test
    void messageOnlyConstructor_usesDefaultErrorCode() {
        PaymentException ex = new PaymentException("something went wrong");
        assertEquals("PAYMENT_ERROR", ex.getErrorCode());
        assertEquals("something went wrong", ex.getMessage());
    }

    @Test
    void messageAndCodeConstructor_usesGivenCode() {
        PaymentException ex = new PaymentException("not found", "PAYMENT_NOT_FOUND");
        assertEquals("PAYMENT_NOT_FOUND", ex.getErrorCode());
    }

    @Test
    void messageAndCauseConstructor_usesDefaultCodeAndKeepsCause() {
        RuntimeException cause = new RuntimeException("root cause");
        PaymentException ex = new PaymentException("wrapped", cause);
        assertEquals("PAYMENT_ERROR", ex.getErrorCode());
        assertSame(cause, ex.getCause());
    }

    @Test
    void insufficientFundsException_exposesAmountsAndCode() {
        InsufficientFundsException ex = new InsufficientFundsException(
                "limit exceeded", new BigDecimal("100.00"), new BigDecimal("25.00"));
        assertEquals("INSUFFICIENT_FUNDS", ex.getErrorCode());
        assertEquals(new BigDecimal("100.00"), ex.getRequestedAmount());
        assertEquals(new BigDecimal("25.00"), ex.getAvailableAmount());
    }
}
