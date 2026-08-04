package com.bigfake.payments.exception;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the payment exception hierarchy.
 */
class PaymentExceptionTest {

    @Test
    void messageOnlyConstructorUsesTheDefaultErrorCode() {
        PaymentException exception = new PaymentException("something went wrong");

        assertEquals("something went wrong", exception.getMessage());
        assertEquals("PAYMENT_ERROR", exception.getErrorCode());
        assertNull(exception.getCause());
    }

    @Test
    void errorCodeConstructorKeepsTheGivenCode() {
        PaymentException exception = new PaymentException("Merchant not found: 1", "MERCHANT_NOT_FOUND");

        assertEquals("MERCHANT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void causeConstructorKeepsTheCauseAndDefaultErrorCode() {
        IllegalStateException cause = new IllegalStateException("gateway timeout");

        PaymentException exception = new PaymentException("gateway call failed", cause);

        assertSame(cause, exception.getCause());
        assertEquals("PAYMENT_ERROR", exception.getErrorCode());
    }

    @Test
    void insufficientFundsExposesRequestedAndAvailableAmounts() {
        InsufficientFundsException exception = new InsufficientFundsException(
                "ACH daily limit exceeded", new BigDecimal("2000.00"), new BigDecimal("1000.00"));

        assertEquals("INSUFFICIENT_FUNDS", exception.getErrorCode());
        assertEquals(new BigDecimal("2000.00"), exception.getRequestedAmount());
        assertEquals(new BigDecimal("1000.00"), exception.getAvailableAmount());
        assertTrue(exception instanceof PaymentException);
    }
}
