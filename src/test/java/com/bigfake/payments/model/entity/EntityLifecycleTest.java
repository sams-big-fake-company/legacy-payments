package com.bigfake.payments.model.entity;

import com.bigfake.payments.model.enums.PaymentStatus;
import com.bigfake.payments.model.enums.PaymentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the JPA lifecycle callbacks on the entity classes.
 * The callbacks are package-private, so these tests live in the entity package.
 */
class EntityLifecycleTest {

    @Test
    void payment_onCreate_setsCreatedAndUpdatedTimestamps() {
        Payment payment = Payment.builder()
                .transactionId("TXN-LIFECYCLE")
                .merchantId(1L)
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .status(PaymentStatus.PENDING)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();

        payment.onCreate();

        assertNotNull(payment.getCreatedAt());
        assertNotNull(payment.getUpdatedAt());
        assertNull(payment.getCompletedAt());
    }

    @Test
    void payment_onUpdate_refreshesUpdatedAtOnly() {
        Payment payment = new Payment();
        payment.onCreate();
        LocalDateTime createdAt = payment.getCreatedAt();
        LocalDateTime firstUpdate = payment.getUpdatedAt();

        payment.onUpdate();

        assertEquals(createdAt, payment.getCreatedAt());
        assertFalse(payment.getUpdatedAt().isBefore(firstUpdate));
    }

    @Test
    void merchant_onCreate_defaultsIsActiveToTrue() {
        Merchant merchant = Merchant.builder()
                .merchantCode("MERCH100")
                .businessName("New Store")
                .build();

        merchant.onCreate();

        assertTrue(merchant.getIsActive());
        assertNotNull(merchant.getCreatedAt());
        assertNotNull(merchant.getUpdatedAt());
    }

    @Test
    void merchant_onCreate_keepsExplicitInactiveFlag() {
        Merchant merchant = Merchant.builder()
                .merchantCode("MERCH101")
                .businessName("Suspended Store")
                .isActive(false)
                .build();

        merchant.onCreate();

        assertFalse(merchant.getIsActive());
    }

    @Test
    void merchant_onUpdate_refreshesUpdatedAt() {
        Merchant merchant = new Merchant();
        merchant.onCreate();
        LocalDateTime firstUpdate = merchant.getUpdatedAt();

        merchant.onUpdate();

        assertFalse(merchant.getUpdatedAt().isBefore(firstUpdate));
    }

    @Test
    void refund_onCreate_setsCreatedAtAndLeavesProcessedAtUnset() {
        Refund refund = Refund.builder()
                .refundId("RFD-LIFECYCLE")
                .paymentId(1L)
                .amount(new BigDecimal("5.00"))
                .status(PaymentStatus.PROCESSING)
                .build();

        refund.onCreate();

        assertNotNull(refund.getCreatedAt());
        assertNull(refund.getProcessedAt());
    }

    @Test
    void paymentMethod_onCreate_setsCreatedAt() {
        PaymentMethod paymentMethod = PaymentMethod.builder()
                .customerId(1L)
                .type(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .cardBrand("VISA")
                .expiryMonth(12)
                .expiryYear(2030)
                .isDefault(true)
                .token("tok_abc")
                .build();

        paymentMethod.onCreate();

        assertNotNull(paymentMethod.getCreatedAt());
        assertEquals("4242", paymentMethod.getCardLastFour());
        assertTrue(paymentMethod.getIsDefault());
    }
}
