package com.bigfake.payments.model.enums;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for PaymentType fee percentages.
 */
class PaymentTypeTest {

    @ParameterizedTest
    @CsvSource({
            "CREDIT_CARD, 0.029",
            "DEBIT, 0.015",
            "WIRE, 0.001",
            "ACH, 0.008"
    })
    @SuppressWarnings("deprecation")
    void getFeePercentage_returnsExpectedRate(PaymentType type, double expected) {
        assertEquals(expected, type.getFeePercentage());
    }
}
