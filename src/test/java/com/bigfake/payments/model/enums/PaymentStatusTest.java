package com.bigfake.payments.model.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentStatusTest {

    @Test
    void isTerminal_completed_true() {
        assertTrue(PaymentStatus.COMPLETED.isTerminal());
    }

    @Test
    void isTerminal_failed_true() {
        assertTrue(PaymentStatus.FAILED.isTerminal());
    }

    @Test
    void isTerminal_refunded_true() {
        assertTrue(PaymentStatus.REFUNDED.isTerminal());
    }

    @Test
    void isTerminal_pending_false() {
        assertFalse(PaymentStatus.PENDING.isTerminal());
    }

    @Test
    void isTerminal_processing_false() {
        assertFalse(PaymentStatus.PROCESSING.isTerminal());
    }

    @Test
    void values_containsAllStatuses() {
        PaymentStatus[] values = PaymentStatus.values();
        assertEquals(5, values.length);
    }

    @Test
    void valueOf_validNames() {
        assertEquals(PaymentStatus.PENDING, PaymentStatus.valueOf("PENDING"));
        assertEquals(PaymentStatus.PROCESSING, PaymentStatus.valueOf("PROCESSING"));
        assertEquals(PaymentStatus.COMPLETED, PaymentStatus.valueOf("COMPLETED"));
        assertEquals(PaymentStatus.FAILED, PaymentStatus.valueOf("FAILED"));
        assertEquals(PaymentStatus.REFUNDED, PaymentStatus.valueOf("REFUNDED"));
    }
}
