package com.bigfake.payments.exception;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PaymentExceptionTest {

    @Test
    void constructor_messageOnly_defaultsErrorCode() {
        PaymentException ex = new PaymentException("something failed");
        assertEquals("something failed", ex.getMessage());
        assertEquals("PAYMENT_ERROR", ex.getErrorCode());
    }

    @Test
    void constructor_messageAndCode() {
        PaymentException ex = new PaymentException("not found", "NOT_FOUND");
        assertEquals("not found", ex.getMessage());
        assertEquals("NOT_FOUND", ex.getErrorCode());
    }

    @Test
    void constructor_messageAndCause_defaultsErrorCode() {
        RuntimeException cause = new RuntimeException("root cause");
        PaymentException ex = new PaymentException("wrapper", cause);
        assertEquals("wrapper", ex.getMessage());
        assertEquals("PAYMENT_ERROR", ex.getErrorCode());
        assertEquals(cause, ex.getCause());
    }
}

class InsufficientFundsExceptionTest {

    @Test
    void constructor_setsAllFields() {
        InsufficientFundsException ex = new InsufficientFundsException(
                "limit exceeded", new BigDecimal("500.00"), new BigDecimal("100.00"));

        assertEquals("limit exceeded", ex.getMessage());
        assertEquals("INSUFFICIENT_FUNDS", ex.getErrorCode());
        assertEquals(new BigDecimal("500.00"), ex.getRequestedAmount());
        assertEquals(new BigDecimal("100.00"), ex.getAvailableAmount());
    }

    @Test
    void extendsPaymentException() {
        InsufficientFundsException ex = new InsufficientFundsException(
                "test", BigDecimal.ONE, BigDecimal.ZERO);
        assertInstanceOf(PaymentException.class, ex);
    }
}
