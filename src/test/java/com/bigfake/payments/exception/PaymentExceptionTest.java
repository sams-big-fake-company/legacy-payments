package com.bigfake.payments.exception;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PaymentExceptionTest {

    @Test
    void constructor_messageOnly_defaultErrorCode() {
        PaymentException ex = new PaymentException("Something failed");
        assertEquals("Something failed", ex.getMessage());
        assertEquals("PAYMENT_ERROR", ex.getErrorCode());
    }

    @Test
    void constructor_messageAndErrorCode() {
        PaymentException ex = new PaymentException("Not found", "NOT_FOUND");
        assertEquals("Not found", ex.getMessage());
        assertEquals("NOT_FOUND", ex.getErrorCode());
    }

    @Test
    void constructor_messageAndCause() {
        RuntimeException cause = new RuntimeException("root cause");
        PaymentException ex = new PaymentException("Wrapper error", cause);
        assertEquals("Wrapper error", ex.getMessage());
        assertEquals("PAYMENT_ERROR", ex.getErrorCode());
        assertEquals(cause, ex.getCause());
    }

    @Test
    void insufficientFundsException_fields() {
        InsufficientFundsException ex = new InsufficientFundsException(
                "Not enough", new BigDecimal("500.00"), new BigDecimal("100.00"));
        assertEquals("Not enough", ex.getMessage());
        assertEquals("INSUFFICIENT_FUNDS", ex.getErrorCode());
        assertEquals(new BigDecimal("500.00"), ex.getRequestedAmount());
        assertEquals(new BigDecimal("100.00"), ex.getAvailableAmount());
    }

    @Test
    void insufficientFundsException_isPaymentException() {
        InsufficientFundsException ex = new InsufficientFundsException(
                "Limit exceeded", new BigDecimal("1000"), new BigDecimal("500"));
        assertTrue(ex instanceof PaymentException);
    }
}
