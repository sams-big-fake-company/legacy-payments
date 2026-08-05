package com.bigfake.payments.model.enums;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the payment enums and their embedded business rules.
 */
class PaymentEnumsTest {

    @Nested
    class PaymentTypeFees {

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
        void everyTypeHasAPositiveFee() {
            for (PaymentType type : PaymentType.values()) {
                assertTrue(type.getFeePercentage() > 0, type + " should have a positive fee");
            }
        }

        @Test
        void valueOf_roundTripsThroughName() {
            assertEquals(PaymentType.WIRE, PaymentType.valueOf("WIRE"));
            assertEquals(4, PaymentType.values().length);
        }
    }

    @Nested
    class PaymentStatusTerminality {

        @ParameterizedTest
        @EnumSource(value = PaymentStatus.class, names = {"COMPLETED", "FAILED", "REFUNDED"})
        void terminalStatuses(PaymentStatus status) {
            assertTrue(status.isTerminal());
        }

        @ParameterizedTest
        @EnumSource(value = PaymentStatus.class, names = {"PENDING", "PROCESSING"})
        void nonTerminalStatuses(PaymentStatus status) {
            assertFalse(status.isTerminal());
        }

        @Test
        void valueOf_roundTripsThroughName() {
            assertEquals(PaymentStatus.PENDING, PaymentStatus.valueOf("PENDING"));
            assertEquals(5, PaymentStatus.values().length);
        }
    }
}
