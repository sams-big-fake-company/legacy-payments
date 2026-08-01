package com.bigfake.payments.exception;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PaymentExceptionTest {

    @Test
    void constructor_messageOnly() {
        PaymentException exception = new PaymentException("Test error");
        assertEquals("Test error", exception.getMessage());
        assertEquals("PAYMENT_ERROR", exception.getErrorCode());
    }

    @Test
    void constructor_messageAndErrorCode() {
        PaymentException exception = new PaymentException("Not found", "NOT_FOUND");
        assertEquals("Not found", exception.getMessage());
        assertEquals("NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void constructor_messageAndCause() {
        RuntimeException cause = new RuntimeException("root cause");
        PaymentException exception = new PaymentException("Wrapper", cause);
        assertEquals("Wrapper", exception.getMessage());
        assertEquals("PAYMENT_ERROR", exception.getErrorCode());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void insufficientFundsException() {
        InsufficientFundsException exception = new InsufficientFundsException(
                "Limit exceeded",
                new BigDecimal("500.00"),
                new BigDecimal("200.00"));

        assertEquals("Limit exceeded", exception.getMessage());
        assertEquals("INSUFFICIENT_FUNDS", exception.getErrorCode());
        assertEquals(new BigDecimal("500.00"), exception.getRequestedAmount());
        assertEquals(new BigDecimal("200.00"), exception.getAvailableAmount());
    }
}
