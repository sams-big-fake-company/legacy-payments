package com.bigfake.payments.exception;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the payment exception hierarchy.
 */
class PaymentExceptionTest {

    @Test
    void messageOnlyConstructor_usesDefaultErrorCode() {
        PaymentException exception = new PaymentException("something went wrong");

        assertEquals("something went wrong", exception.getMessage());
        assertEquals("PAYMENT_ERROR", exception.getErrorCode());
        assertNull(exception.getCause());
    }

    @Test
    void messageAndCodeConstructor_keepsExplicitErrorCode() {
        PaymentException exception = new PaymentException("merchant is inactive", "MERCHANT_INACTIVE");

        assertEquals("merchant is inactive", exception.getMessage());
        assertEquals("MERCHANT_INACTIVE", exception.getErrorCode());
    }

    @Test
    void causeConstructor_retainsCauseAndDefaultErrorCode() {
        IllegalStateException cause = new IllegalStateException("gateway timeout");

        PaymentException exception = new PaymentException("gateway call failed", cause);

        assertSame(cause, exception.getCause());
        assertEquals("PAYMENT_ERROR", exception.getErrorCode());
    }

    @Test
    void insufficientFunds_carriesAmountsAndFixedErrorCode() {
        InsufficientFundsException exception = new InsufficientFundsException(
                "Merchant daily limit would be exceeded", new BigDecimal("750.00"), new BigDecimal("100.00"));

        assertTrue(exception instanceof PaymentException);
        assertEquals("INSUFFICIENT_FUNDS", exception.getErrorCode());
        assertEquals(new BigDecimal("750.00"), exception.getRequestedAmount());
        assertEquals(new BigDecimal("100.00"), exception.getAvailableAmount());
        assertEquals("Merchant daily limit would be exceeded", exception.getMessage());
    }
}
