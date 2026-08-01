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

    @Test
    void allTypeValues_exist() {
        PaymentType[] values = PaymentType.values();
        assertEquals(4, values.length);
    }

    @Test
    void valueOf_returnsCorrectType() {
        assertEquals(PaymentType.CREDIT_CARD, PaymentType.valueOf("CREDIT_CARD"));
        assertEquals(PaymentType.DEBIT, PaymentType.valueOf("DEBIT"));
        assertEquals(PaymentType.WIRE, PaymentType.valueOf("WIRE"));
        assertEquals(PaymentType.ACH, PaymentType.valueOf("ACH"));
    }

    @Test
    void getFeePercentage_allTypesReturnPositiveValue() {
        for (PaymentType type : PaymentType.values()) {
            assertTrue(type.getFeePercentage() > 0, type + " should have positive fee");
        }
    }
}
