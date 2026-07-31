package com.bigfake.payments.model.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the behaviour attached to the payment enums.
 */
class PaymentEnumsTest {

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"COMPLETED", "FAILED", "REFUNDED"})
    void isTerminal_trueForFinalStates(PaymentStatus status) {
        assertTrue(status.isTerminal());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"PENDING", "PROCESSING"})
    void isTerminal_falseForInFlightStates(PaymentStatus status) {
        assertFalse(status.isTerminal());
    }

    @Test
    void getFeePercentage_matchesPricingPerPaymentType() {
        assertEquals(0.029, PaymentType.CREDIT_CARD.getFeePercentage(), 0.0);
        assertEquals(0.015, PaymentType.DEBIT.getFeePercentage(), 0.0);
        assertEquals(0.001, PaymentType.WIRE.getFeePercentage(), 0.0);
        assertEquals(0.008, PaymentType.ACH.getFeePercentage(), 0.0);
    }

    @ParameterizedTest
    @EnumSource(PaymentType.class)
    void getFeePercentage_isAlwaysBetweenZeroAndThreePercent(PaymentType type) {
        double fee = type.getFeePercentage();

        assertTrue(fee > 0.0 && fee <= 0.03, "unexpected fee for " + type + ": " + fee);
    }
}
