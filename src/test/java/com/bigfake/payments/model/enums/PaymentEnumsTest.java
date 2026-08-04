package com.bigfake.payments.model.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the payment enums and their embedded business rules.
 */
class PaymentEnumsTest {

    @ParameterizedTest
    @CsvSource({
            "CREDIT_CARD, 0.029",
            "DEBIT, 0.015",
            "WIRE, 0.001",
            "ACH, 0.008"
    })
    void paymentType_feePercentagePerType(PaymentType type, double expectedFee) {
        assertEquals(expectedFee, type.getFeePercentage());
    }

    @ParameterizedTest
    @EnumSource(PaymentType.class)
    void paymentType_feePercentageIsAlwaysBetweenZeroAndThreePercent(PaymentType type) {
        assertTrue(type.getFeePercentage() > 0, type + " should have a positive fee");
        assertTrue(type.getFeePercentage() <= 0.03, type + " should not exceed 3%");
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"COMPLETED", "FAILED", "REFUNDED"})
    void paymentStatus_terminalStates(PaymentStatus status) {
        assertTrue(status.isTerminal());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"PENDING", "PROCESSING"})
    void paymentStatus_nonTerminalStates(PaymentStatus status) {
        assertFalse(status.isTerminal());
    }

    @Test
    void paymentStatus_hasTheExpectedLifecycleValues() {
        assertArrayEquals(
                new PaymentStatus[]{
                        PaymentStatus.PENDING,
                        PaymentStatus.PROCESSING,
                        PaymentStatus.COMPLETED,
                        PaymentStatus.FAILED,
                        PaymentStatus.REFUNDED},
                PaymentStatus.values());
    }
}
