package com.bigfake.payments.model.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentTypeTest {

    @Test
    void getFeePercentage_creditCard() {
        assertEquals(0.029, PaymentType.CREDIT_CARD.getFeePercentage());
    }

    @Test
    void getFeePercentage_debit() {
        assertEquals(0.015, PaymentType.DEBIT.getFeePercentage());
    }

    @Test
    void getFeePercentage_wire() {
        assertEquals(0.001, PaymentType.WIRE.getFeePercentage());
    }

    @Test
    void getFeePercentage_ach() {
        assertEquals(0.008, PaymentType.ACH.getFeePercentage());
    }
}
