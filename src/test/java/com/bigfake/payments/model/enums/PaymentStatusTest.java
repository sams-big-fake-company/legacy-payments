package com.bigfake.payments.model.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentStatusTest {

    @Test
    void isTerminal_completed() {
        assertTrue(PaymentStatus.COMPLETED.isTerminal());
    }

    @Test
    void isTerminal_failed() {
        assertTrue(PaymentStatus.FAILED.isTerminal());
    }

    @Test
    void isTerminal_refunded() {
        assertTrue(PaymentStatus.REFUNDED.isTerminal());
    }

    @Test
    void isTerminal_pending_notTerminal() {
        assertFalse(PaymentStatus.PENDING.isTerminal());
    }

    @Test
    void isTerminal_processing_notTerminal() {
        assertFalse(PaymentStatus.PROCESSING.isTerminal());
    }
}
