package com.bigfake.payments.model.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentStatusTest {

    @Test
    void isTerminal_completed_returnsTrue() {
        assertTrue(PaymentStatus.COMPLETED.isTerminal());
    }

    @Test
    void isTerminal_failed_returnsTrue() {
        assertTrue(PaymentStatus.FAILED.isTerminal());
    }

    @Test
    void isTerminal_refunded_returnsTrue() {
        assertTrue(PaymentStatus.REFUNDED.isTerminal());
    }

    @Test
    void isTerminal_pending_returnsFalse() {
        assertFalse(PaymentStatus.PENDING.isTerminal());
    }

    @Test
    void isTerminal_processing_returnsFalse() {
        assertFalse(PaymentStatus.PROCESSING.isTerminal());
    }

    @Test
    void values_containsAllStatuses() {
        PaymentStatus[] values = PaymentStatus.values();
        assertEquals(5, values.length);
    }
}
