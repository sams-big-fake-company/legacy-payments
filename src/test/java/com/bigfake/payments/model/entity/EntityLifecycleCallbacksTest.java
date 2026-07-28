package com.bigfake.payments.model.entity;

import com.bigfake.payments.model.enums.PaymentType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the JPA lifecycle callbacks that stamp timestamps and apply defaults.
 * They live in the entity package because the callbacks are package visible.
 */
class EntityLifecycleCallbacksTest {

    @Test
    void paymentIsStampedOnPersistAndOnUpdate() {
        Payment payment = Payment.builder().build();
        LocalDateTime before = LocalDateTime.now();

        payment.onCreate();

        assertNotNull(payment.getCreatedAt());
        assertFalse(payment.getCreatedAt().isBefore(before));
        assertNotNull(payment.getUpdatedAt());

        LocalDateTime createdAt = payment.getCreatedAt();
        LocalDateTime firstUpdate = payment.getUpdatedAt();
        payment.onUpdate();

        assertEquals(createdAt, payment.getCreatedAt());
        assertFalse(payment.getUpdatedAt().isBefore(firstUpdate));
    }

    @Test
    void merchantDefaultsToActiveOnPersist() {
        Merchant merchant = Merchant.builder().merchantCode("MERCH002").build();

        merchant.onCreate();

        assertTrue(merchant.getIsActive());
        assertNotNull(merchant.getCreatedAt());
        assertNotNull(merchant.getUpdatedAt());
    }

    @Test
    void merchantKeepsAnExplicitActiveFlagOnPersist() {
        Merchant merchant = Merchant.builder().merchantCode("MERCH003").isActive(false).build();

        merchant.onCreate();

        assertFalse(merchant.getIsActive());
    }

    @Test
    void merchantIsStampedOnUpdate() {
        Merchant merchant = Merchant.builder().build();

        merchant.onUpdate();

        assertNotNull(merchant.getUpdatedAt());
    }

    @Test
    void refundIsStampedOnPersist() {
        Refund refund = Refund.builder().refundId("RFD-1").build();

        refund.onCreate();

        assertNotNull(refund.getCreatedAt());
    }

    @Test
    void paymentMethodIsStampedOnPersist() {
        PaymentMethod paymentMethod = PaymentMethod.builder()
                .customerId(5L)
                .type(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .build();

        paymentMethod.onCreate();

        assertNotNull(paymentMethod.getCreatedAt());
        assertEquals("4242", paymentMethod.getCardLastFour());
        assertEquals(PaymentType.CREDIT_CARD, paymentMethod.getType());
    }
}
