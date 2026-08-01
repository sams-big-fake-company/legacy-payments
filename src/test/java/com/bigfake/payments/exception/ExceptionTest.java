package com.bigfake.payments.exception;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionTest {

    // --- PaymentException ---

    @Test
    void paymentException_messageOnly_hasDefaultErrorCode() {
        PaymentException ex = new PaymentException("Something went wrong");
        assertEquals("Something went wrong", ex.getMessage());
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

    // --- InsufficientFundsException ---

    @Test
    void insufficientFundsException_hasCorrectFields() {
        InsufficientFundsException ex = new InsufficientFundsException(
                "Not enough funds",
                new BigDecimal("500.00"),
                new BigDecimal("100.00")
        );
        assertEquals("Not enough funds", ex.getMessage());
        assertEquals("INSUFFICIENT_FUNDS", ex.getErrorCode());
        assertEquals(new BigDecimal("500.00"), ex.getRequestedAmount());
        assertEquals(new BigDecimal("100.00"), ex.getAvailableAmount());
    }

    @Test
    void insufficientFundsException_isPaymentException() {
        InsufficientFundsException ex = new InsufficientFundsException(
                "msg", BigDecimal.ONE, BigDecimal.ZERO);
        assertTrue(ex instanceof PaymentException);
    }
}
