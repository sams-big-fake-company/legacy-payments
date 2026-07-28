package com.bigfake.payments.model.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the fee table on {@link PaymentType} and the terminal-state rule on
 * {@link PaymentStatus}.
 */
class PaymentEnumsTest {

    @ParameterizedTest(name = "{0} is charged {1}")
    @CsvSource({
            "CREDIT_CARD, 0.029",
            "DEBIT, 0.015",
            "WIRE, 0.001",
            "ACH, 0.008"
    })
    void feePercentagePerPaymentType(PaymentType type, double expectedFee) {
        assertEquals(expectedFee, type.getFeePercentage());
    }

    @ParameterizedTest(name = "{0} is terminal")
    @EnumSource(value = PaymentStatus.class, names = {"COMPLETED", "FAILED", "REFUNDED"})
    void terminalStatuses(PaymentStatus status) {
        assertTrue(status.isTerminal());
    }

    @ParameterizedTest(name = "{0} is not terminal")
    @EnumSource(value = PaymentStatus.class, names = {"PENDING", "PROCESSING"})
    void nonTerminalStatuses(PaymentStatus status) {
        assertFalse(status.isTerminal());
    }

    @Test
    void enumsExposeTheExpectedConstants() {
        assertEquals(4, PaymentType.values().length);
        assertEquals(5, PaymentStatus.values().length);
        assertEquals(PaymentType.ACH, PaymentType.valueOf("ACH"));
        assertEquals(PaymentStatus.REFUNDED, PaymentStatus.valueOf("REFUNDED"));
    }
}
