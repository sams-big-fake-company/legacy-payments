package com.bigfake.payments.model.enums;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for PaymentStatus terminal-state logic.
 */
class PaymentStatusTest {

    @ParameterizedTest
    @CsvSource({
            "PENDING, false",
            "PROCESSING, false",
            "COMPLETED, true",
            "FAILED, true",
            "REFUNDED, true"
    })
    @SuppressWarnings("deprecation")
    void isTerminal_returnsExpected(PaymentStatus status, boolean expected) {
        assertEquals(expected, status.isTerminal());
    }
}
