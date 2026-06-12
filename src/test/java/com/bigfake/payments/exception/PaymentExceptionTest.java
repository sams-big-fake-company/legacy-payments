package com.bigfake.payments.exception;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentExceptionTest {

    @Test
    void messageOnlyConstructor_usesDefaultErrorCode() {
        PaymentException ex = new PaymentException("something failed");
        assertEquals("something failed", ex.getMessage());
        assertEquals("PAYMENT_ERROR", ex.getErrorCode());
    }

    @Test
    void messageAndCodeConstructor_setsErrorCode() {
        PaymentException ex = new PaymentException("merchant missing", "MERCHANT_NOT_FOUND");
        assertEquals("merchant missing", ex.getMessage());
        assertEquals("MERCHANT_NOT_FOUND", ex.getErrorCode());
    }

    @Test
    void messageAndCauseConstructor_usesDefaultErrorCode() {
        RuntimeException cause = new RuntimeException("root");
        PaymentException ex = new PaymentException("wrapped", cause);
        assertEquals("wrapped", ex.getMessage());
        assertEquals(cause, ex.getCause());
        assertEquals("PAYMENT_ERROR", ex.getErrorCode());
    }

    @Test
    void insufficientFundsException_exposesAmounts() {
        InsufficientFundsException ex = new InsufficientFundsException(
                "limit exceeded", new BigDecimal("200.00"), new BigDecimal("50.00"));
        assertEquals("INSUFFICIENT_FUNDS", ex.getErrorCode());
        assertEquals(new BigDecimal("200.00"), ex.getRequestedAmount());
        assertEquals(new BigDecimal("50.00"), ex.getAvailableAmount());
    }
}
