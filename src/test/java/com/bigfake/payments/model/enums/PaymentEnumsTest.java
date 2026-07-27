package com.bigfake.payments.model.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the payment enums.
 */
class PaymentEnumsTest {

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"COMPLETED", "FAILED", "REFUNDED"})
    void isTerminal_terminalStatuses_returnTrue(PaymentStatus status) {
        assertTrue(status.isTerminal());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"PENDING", "PROCESSING"})
    void isTerminal_inFlightStatuses_returnFalse(PaymentStatus status) {
        assertFalse(status.isTerminal());
    }

    @Test
    void paymentStatus_declaresExpectedValues() {
        assertEquals(5, PaymentStatus.values().length);
        assertEquals(PaymentStatus.REFUNDED, PaymentStatus.valueOf("REFUNDED"));
    }

    @ParameterizedTest
    @CsvSource({
            "CREDIT_CARD, 0.029",
            "DEBIT, 0.015",
            "WIRE, 0.001",
            "ACH, 0.008"
    })
    void getFeePercentage_returnsTypeSpecificRate(PaymentType type, double expected) {
        assertEquals(expected, type.getFeePercentage());
    }

    @Test
    void paymentType_declaresExpectedValues() {
        assertEquals(4, PaymentType.values().length);
        assertEquals(PaymentType.ACH, PaymentType.valueOf("ACH"));
    }
}
