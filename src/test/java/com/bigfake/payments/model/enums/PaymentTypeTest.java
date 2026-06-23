package com.bigfake.payments.model.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentTypeTest {

    @Test
    void getFeePercentage_creditCard() {
        assertEquals(0.029, PaymentType.CREDIT_CARD.getFeePercentage(), 0.001);
    }

    @Test
    void getFeePercentage_debit() {
        assertEquals(0.015, PaymentType.DEBIT.getFeePercentage(), 0.001);
    }

    @Test
    void getFeePercentage_wire() {
        assertEquals(0.001, PaymentType.WIRE.getFeePercentage(), 0.001);
    }

    @Test
    void getFeePercentage_ach() {
        assertEquals(0.008, PaymentType.ACH.getFeePercentage(), 0.001);
    }

    @Test
    void allValuesExist() {
        PaymentType[] values = PaymentType.values();
        assertEquals(4, values.length);
        assertNotNull(PaymentType.valueOf("CREDIT_CARD"));
        assertNotNull(PaymentType.valueOf("DEBIT"));
        assertNotNull(PaymentType.valueOf("WIRE"));
        assertNotNull(PaymentType.valueOf("ACH"));
    }
}
