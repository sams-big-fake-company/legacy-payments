package com.bigfake.payments.model.entity;

import com.bigfake.payments.model.enums.PaymentStatus;
import com.bigfake.payments.model.enums.PaymentType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the JPA lifecycle callbacks on the entities. These run on
 * persist/update in production, so they are exercised directly here.
 */
class EntityLifecycleCallbackTest {

    private static void invokeCallback(Object entity, String method) {
        ReflectionTestUtils.invokeMethod(entity, method);
    }

    @Test
    void paymentStampsCreatedAndUpdatedTimestampsOnPersist() {
        Payment payment = Payment.builder()
                .transactionId("TXN-LIFECYCLE1")
                .merchantId(1L)
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .status(PaymentStatus.PENDING)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();

        invokeCallback(payment, "onCreate");

        assertNotNull(payment.getCreatedAt());
        assertNotNull(payment.getUpdatedAt());
        assertNull(payment.getCompletedAt());
    }

    @Test
    void paymentRefreshesOnlyTheUpdatedTimestampOnUpdate() {
        Payment payment = new Payment();
        LocalDateTime created = LocalDateTime.now().minusDays(1);
        payment.setCreatedAt(created);
        payment.setUpdatedAt(created);

        invokeCallback(payment, "onUpdate");

        assertEquals(created, payment.getCreatedAt());
        assertTrue(payment.getUpdatedAt().isAfter(created));
    }

    @Test
    void merchantDefaultsToActiveWhenTheFlagIsNotSet() {
        Merchant merchant = Merchant.builder()
                .merchantCode("MERCH100")
                .businessName("New Store")
                .build();

        invokeCallback(merchant, "onCreate");

        assertTrue(merchant.getIsActive());
        assertNotNull(merchant.getCreatedAt());
        assertNotNull(merchant.getUpdatedAt());
    }

    @Test
    void merchantKeepsAnExplicitlyInactiveFlag() {
        Merchant merchant = Merchant.builder()
                .merchantCode("MERCH101")
                .businessName("Suspended Store")
                .isActive(false)
                .build();

        invokeCallback(merchant, "onCreate");

        assertFalse(merchant.getIsActive());
    }

    @Test
    void merchantRefreshesOnlyTheUpdatedTimestampOnUpdate() {
        Merchant merchant = new Merchant();
        LocalDateTime created = LocalDateTime.now().minusDays(1);
        merchant.setCreatedAt(created);
        merchant.setUpdatedAt(created);

        invokeCallback(merchant, "onUpdate");

        assertEquals(created, merchant.getCreatedAt());
        assertTrue(merchant.getUpdatedAt().isAfter(created));
    }

    @Test
    void refundStampsItsCreationTimestamp() {
        Refund refund = Refund.builder()
                .refundId("RFD-LIFECYCLE1")
                .paymentId(1L)
                .amount(new BigDecimal("5.00"))
                .status(PaymentStatus.PROCESSING)
                .build();

        invokeCallback(refund, "onCreate");

        assertNotNull(refund.getCreatedAt());
        assertNull(refund.getProcessedAt());
    }

    @Test
    void paymentMethodStampsItsCreationTimestamp() {
        PaymentMethod paymentMethod = PaymentMethod.builder()
                .customerId(1L)
                .type(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .cardBrand("VISA")
                .expiryMonth(12)
                .expiryYear(2030)
                .isDefault(true)
                .token("tok_123")
                .build();

        invokeCallback(paymentMethod, "onCreate");

        assertNotNull(paymentMethod.getCreatedAt());
        assertEquals(PaymentType.CREDIT_CARD, paymentMethod.getType());
        assertTrue(paymentMethod.getIsDefault());
    }
}
