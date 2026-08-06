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
 * Unit tests for the JPA lifecycle callbacks on the entities. These run without a
 * persistence context - the callbacks are plain methods that set audit fields.
 */
class EntityLifecycleCallbacksTest {

    @Test
    void paymentOnCreateStampsCreatedAndUpdated() {
        Payment payment = Payment.builder()
                .transactionId("TXN-1")
                .merchantId(1L)
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .status(PaymentStatus.PENDING)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();

        payment.onCreate();

        assertNotNull(payment.getCreatedAt());
        assertNotNull(payment.getUpdatedAt());
        assertFalse(payment.getUpdatedAt().isBefore(payment.getCreatedAt()));
        assertNull(payment.getCompletedAt());
    }

    @Test
    void paymentOnUpdateOnlyMovesUpdatedAt() {
        Payment payment = Payment.builder().transactionId("TXN-1").build();
        payment.onCreate();
        LocalDateTime createdAt = payment.getCreatedAt();

        payment.onUpdate();

        assertEquals(createdAt, payment.getCreatedAt());
        assertFalse(payment.getUpdatedAt().isBefore(createdAt));
    }

    @Test
    void merchantOnCreateDefaultsToActive() {
        Merchant merchant = Merchant.builder()
                .merchantCode("MERCH001")
                .businessName("Test Store")
                .build();

        merchant.onCreate();

        assertTrue(merchant.getIsActive());
        assertNotNull(merchant.getCreatedAt());
        assertNotNull(merchant.getUpdatedAt());
        assertFalse(merchant.getUpdatedAt().isBefore(merchant.getCreatedAt()));
    }

    @Test
    void merchantOnCreateKeepsExplicitInactiveFlag() {
        Merchant merchant = Merchant.builder()
                .merchantCode("MERCH002")
                .businessName("Suspended Store")
                .isActive(false)
                .build();

        merchant.onCreate();

        assertFalse(merchant.getIsActive());
    }

    @Test
    void merchantOnUpdateMovesUpdatedAt() {
        Merchant merchant = Merchant.builder().merchantCode("MERCH001").build();
        merchant.onCreate();
        LocalDateTime createdAt = merchant.getCreatedAt();

        merchant.onUpdate();

        assertEquals(createdAt, merchant.getCreatedAt());
        assertFalse(merchant.getUpdatedAt().isBefore(createdAt));
    }

    @Test
    void refundOnCreateStampsCreatedAt() {
        Refund refund = Refund.builder()
                .refundId("RFD-1")
                .paymentId(1L)
                .amount(new BigDecimal("5.00"))
                .status(PaymentStatus.PROCESSING)
                .build();

        refund.onCreate();

        assertNotNull(refund.getCreatedAt());
        assertNull(refund.getProcessedAt());
    }

    @Test
    void paymentMethodOnCreateStampsCreatedAt() {
        PaymentMethod paymentMethod = PaymentMethod.builder()
                .customerId(1L)
                .type(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .cardBrand("VISA")
                .isDefault(true)
                .build();

        paymentMethod.onCreate();

        assertNotNull(paymentMethod.getCreatedAt());
        assertEquals("4242", paymentMethod.getCardLastFour());
    }
}
