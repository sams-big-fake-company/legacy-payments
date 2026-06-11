package com.bigfake.payments.model.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentStatusTest {

    @Test
    void isTerminal_terminalStates_returnTrue() {
        assertTrue(PaymentStatus.COMPLETED.isTerminal());
        assertTrue(PaymentStatus.FAILED.isTerminal());
        assertTrue(PaymentStatus.REFUNDED.isTerminal());
    }

    @Test
    void isTerminal_nonTerminalStates_returnFalse() {
        assertFalse(PaymentStatus.PENDING.isTerminal());
        assertFalse(PaymentStatus.PROCESSING.isTerminal());
    }
}
