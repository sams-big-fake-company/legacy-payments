package com.bigfake.payments.model.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class PaymentEnumsTest {

    @ParameterizedTest
    @CsvSource({
            "CREDIT_CARD, 0.029",
            "DEBIT, 0.015",
            "WIRE, 0.001",
            "ACH, 0.008"
    })
    void getFeePercentage_returnsExpectedRate(PaymentType type, double expected) {
        assertEquals(expected, type.getFeePercentage());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"COMPLETED", "FAILED", "REFUNDED"})
    void isTerminal_trueForTerminalStates(PaymentStatus status) {
        assertTrue(status.isTerminal());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"PENDING", "PROCESSING"})
    void isTerminal_falseForNonTerminalStates(PaymentStatus status) {
        assertFalse(status.isTerminal());
    }
}
