package com.bigfake.payments.exception;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentExceptionTest {

    @Test
    void messageOnlyConstructor_usesDefaultErrorCode() {
        PaymentException exception = new PaymentException("something went wrong");
        assertEquals("something went wrong", exception.getMessage());
        assertEquals("PAYMENT_ERROR", exception.getErrorCode());
    }

    @Test
    void messageAndCodeConstructor_usesProvidedCode() {
        PaymentException exception = new PaymentException("not found", "PAYMENT_NOT_FOUND");
        assertEquals("not found", exception.getMessage());
        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void messageAndCauseConstructor_usesDefaultErrorCodeAndKeepsCause() {
        RuntimeException cause = new RuntimeException("root cause");
        PaymentException exception = new PaymentException("wrapped", cause);
        assertEquals("wrapped", exception.getMessage());
        assertEquals("PAYMENT_ERROR", exception.getErrorCode());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void insufficientFundsException_exposesAmountsAndCode() {
        InsufficientFundsException exception = new InsufficientFundsException(
                "limit exceeded", new BigDecimal("100.00"), new BigDecimal("25.00"));
        assertEquals("limit exceeded", exception.getMessage());
        assertEquals("INSUFFICIENT_FUNDS", exception.getErrorCode());
        assertEquals(new BigDecimal("100.00"), exception.getRequestedAmount());
        assertEquals(new BigDecimal("25.00"), exception.getAvailableAmount());
    }
}
