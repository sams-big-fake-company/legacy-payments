package com.bigfake.payments.model.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentStatusTest {

    @Test
    void isTerminal_completedIsTerminal() {
        assertTrue(PaymentStatus.COMPLETED.isTerminal());
    }

    @Test
    void isTerminal_failedIsTerminal() {
        assertTrue(PaymentStatus.FAILED.isTerminal());
    }

    @Test
    void isTerminal_refundedIsTerminal() {
        assertTrue(PaymentStatus.REFUNDED.isTerminal());
    }

    @Test
    void isTerminal_pendingIsNotTerminal() {
        assertFalse(PaymentStatus.PENDING.isTerminal());
    }

    @Test
    void isTerminal_processingIsNotTerminal() {
        assertFalse(PaymentStatus.PROCESSING.isTerminal());
    }

    @Test
    void allStatusValues_exist() {
        PaymentStatus[] values = PaymentStatus.values();
        assertEquals(5, values.length);
    }

    @Test
    void valueOf_returnsCorrectStatus() {
        assertEquals(PaymentStatus.PENDING, PaymentStatus.valueOf("PENDING"));
        assertEquals(PaymentStatus.PROCESSING, PaymentStatus.valueOf("PROCESSING"));
        assertEquals(PaymentStatus.COMPLETED, PaymentStatus.valueOf("COMPLETED"));
        assertEquals(PaymentStatus.FAILED, PaymentStatus.valueOf("FAILED"));
        assertEquals(PaymentStatus.REFUNDED, PaymentStatus.valueOf("REFUNDED"));
    }
}
