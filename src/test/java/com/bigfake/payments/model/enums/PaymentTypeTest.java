package com.bigfake.payments.model.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the PaymentType fee table.
 */
class PaymentTypeTest {

    @ParameterizedTest
    @CsvSource({
            "CREDIT_CARD, 0.029",
            "DEBIT,       0.015",
            "WIRE,        0.001",
            "ACH,         0.008"
    })
    void feePercentageMatchesTheAgreedRate(PaymentType type, double expectedFee) {
        assertEquals(expectedFee, type.getFeePercentage());
    }

    @Test
    void everyTypeHasAPositiveFee() {
        for (PaymentType type : PaymentType.values()) {
            assertTrue(type.getFeePercentage() > 0, type + " should have a positive fee");
        }
    }

    @Test
    void wireIsTheCheapestAndCreditCardTheMostExpensive() {
        assertEquals(PaymentType.WIRE, cheapest());
        assertTrue(PaymentType.CREDIT_CARD.getFeePercentage() > PaymentType.DEBIT.getFeePercentage());
    }

    private static PaymentType cheapest() {
        PaymentType cheapest = PaymentType.values()[0];
        for (PaymentType type : PaymentType.values()) {
            if (type.getFeePercentage() < cheapest.getFeePercentage()) {
                cheapest = type;
            }
        }
        return cheapest;
    }
}
