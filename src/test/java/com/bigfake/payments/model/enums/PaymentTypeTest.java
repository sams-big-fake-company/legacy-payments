package com.bigfake.payments.model.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the PaymentType enum.
 */
class PaymentTypeTest {

    @ParameterizedTest
    @CsvSource({
            "CREDIT_CARD, 0.029",
            "DEBIT, 0.015",
            "WIRE, 0.001",
            "ACH, 0.008"
    })
    void feePercentageMatchesPricingTable(PaymentType type, double expectedFee) {
        assertEquals(expectedFee, type.getFeePercentage(), 0.0);
    }

    @ParameterizedTest
    @EnumSource(PaymentType.class)
    void feePercentageIsAlwaysBetweenZeroAndOne(PaymentType type) {
        double fee = type.getFeePercentage();

        assertTrue(fee > 0.0 && fee < 1.0, "unexpected fee for " + type + ": " + fee);
    }

    @Test
    void valueOfRoundTripsThroughName() {
        for (PaymentType type : PaymentType.values()) {
            assertEquals(type, PaymentType.valueOf(type.name()));
        }
    }

    @Test
    void declaresExactlyTheSupportedPaymentTypes() {
        assertEquals(4, PaymentType.values().length);
    }
}
