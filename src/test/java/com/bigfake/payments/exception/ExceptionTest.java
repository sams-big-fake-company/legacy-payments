package com.bigfake.payments.exception;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionTest {

    // ---- PaymentException ----

    @Test
    void paymentException_messageOnly() {
        PaymentException ex = new PaymentException("Something failed");
        assertEquals("Something failed", ex.getMessage());
        assertEquals("PAYMENT_ERROR", ex.getErrorCode());
    }

    @Test
    void paymentException_messageAndCode() {
        PaymentException ex = new PaymentException("Not found", "NOT_FOUND");
        assertEquals("Not found", ex.getMessage());
        assertEquals("NOT_FOUND", ex.getErrorCode());
    }

    @Test
    void paymentException_messageAndCause() {
        RuntimeException cause = new RuntimeException("root cause");
        PaymentException ex = new PaymentException("Wrapper", cause);
        assertEquals("Wrapper", ex.getMessage());
        assertEquals("PAYMENT_ERROR", ex.getErrorCode());
        assertEquals(cause, ex.getCause());
    }

    // ---- InsufficientFundsException ----

    @Test
    void insufficientFundsException_fields() {
        InsufficientFundsException ex = new InsufficientFundsException(
                "Limit exceeded",
                new BigDecimal("500.00"),
                new BigDecimal("300.00")
        );
        assertEquals("Limit exceeded", ex.getMessage());
        assertEquals("INSUFFICIENT_FUNDS", ex.getErrorCode());
        assertEquals(new BigDecimal("500.00"), ex.getRequestedAmount());
        assertEquals(new BigDecimal("300.00"), ex.getAvailableAmount());
    }

    @Test
    void insufficientFundsException_isPaymentException() {
        InsufficientFundsException ex = new InsufficientFundsException(
                "Test", BigDecimal.ONE, BigDecimal.ZERO);
        assertInstanceOf(PaymentException.class, ex);
    }
}
