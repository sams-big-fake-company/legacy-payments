package com.bigfake.payments.model.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the PaymentStatus enum.
 */
class PaymentStatusTest {

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"COMPLETED", "FAILED", "REFUNDED"})
    void terminalStatusesAreTerminal(PaymentStatus status) {
        assertTrue(status.isTerminal());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"PENDING", "PROCESSING"})
    void inFlightStatusesAreNotTerminal(PaymentStatus status) {
        assertFalse(status.isTerminal());
    }

    @Test
    void valueOfRoundTripsThroughName() {
        for (PaymentStatus status : PaymentStatus.values()) {
            assertEquals(status, PaymentStatus.valueOf(status.name()));
        }
    }

    @Test
    void declaresExactlyTheSupportedStatuses() {
        assertEquals(5, PaymentStatus.values().length);
    }
}
