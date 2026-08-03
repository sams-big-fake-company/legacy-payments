package com.bigfake.payments.model.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for PaymentType.
 */
class PaymentTypeTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "CREDIT_CARD, 0.029",
            "DEBIT, 0.015",
            "WIRE, 0.001",
            "ACH, 0.008"
    })
    void exposesTheProcessingFeeForEachType(PaymentType type, double expectedFee) {
        assertEquals(expectedFee, type.getFeePercentage());
    }

    @ParameterizedTest
    @EnumSource(PaymentType.class)
    void everyTypeHasAPositiveFeeBelowFivePercent(PaymentType type) {
        double fee = type.getFeePercentage();

        assertTrue(fee > 0 && fee < 0.05, "unexpected fee for " + type + ": " + fee);
    }

    @Test
    void cardTypesAreTheMostExpensiveToProcess() {
        assertTrue(PaymentType.CREDIT_CARD.getFeePercentage() > PaymentType.DEBIT.getFeePercentage());
        assertTrue(PaymentType.DEBIT.getFeePercentage() > PaymentType.ACH.getFeePercentage());
        assertTrue(PaymentType.ACH.getFeePercentage() > PaymentType.WIRE.getFeePercentage());
    }
}
