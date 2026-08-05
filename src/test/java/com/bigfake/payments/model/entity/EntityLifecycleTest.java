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
 * Unit tests for the JPA lifecycle callbacks on the entity classes.
 * The callbacks are the only hand-written logic on these entities.
 */
class EntityLifecycleTest {

    @Test
    void payment_onCreate_setsCreatedAndUpdatedTimestamps() {
        Payment payment = Payment.builder()
                .transactionId("TXN-ABC123")
                .merchantId(1L)
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .status(PaymentStatus.PENDING)
                .paymentType(PaymentType.ACH)
                .build();

        ReflectionTestUtils.invokeMethod(payment, "onCreate");

        assertNotNull(payment.getCreatedAt());
        assertNotNull(payment.getUpdatedAt());
        assertFalse(payment.getUpdatedAt().isBefore(payment.getCreatedAt()));
        assertNull(payment.getCompletedAt());
    }

    @Test
    void payment_onUpdate_refreshesUpdatedTimestampOnly() {
        Payment payment = Payment.builder().build();
        LocalDateTime created = LocalDateTime.now().minusDays(1);
        payment.setCreatedAt(created);
        payment.setUpdatedAt(created);

        ReflectionTestUtils.invokeMethod(payment, "onUpdate");

        assertEquals(created, payment.getCreatedAt());
        assertTrue(payment.getUpdatedAt().isAfter(created));
    }

    @Test
    void merchant_onCreate_defaultsIsActiveToTrue() {
        Merchant merchant = Merchant.builder()
                .merchantCode("MERCH001")
                .businessName("Test Store")
                .build();

        ReflectionTestUtils.invokeMethod(merchant, "onCreate");

        assertEquals(Boolean.TRUE, merchant.getIsActive());
        assertNotNull(merchant.getCreatedAt());
        assertNotNull(merchant.getUpdatedAt());
        assertFalse(merchant.getUpdatedAt().isBefore(merchant.getCreatedAt()));
    }

    @Test
    void merchant_onCreate_preservesExplicitInactiveFlag() {
        Merchant merchant = Merchant.builder()
                .merchantCode("MERCH002")
                .businessName("Closed Store")
                .isActive(false)
                .build();

        ReflectionTestUtils.invokeMethod(merchant, "onCreate");

        assertEquals(Boolean.FALSE, merchant.getIsActive());
    }

    @Test
    void merchant_onUpdate_refreshesUpdatedTimestampOnly() {
        Merchant merchant = Merchant.builder().build();
        LocalDateTime created = LocalDateTime.now().minusHours(2);
        merchant.setCreatedAt(created);
        merchant.setUpdatedAt(created);

        ReflectionTestUtils.invokeMethod(merchant, "onUpdate");

        assertEquals(created, merchant.getCreatedAt());
        assertTrue(merchant.getUpdatedAt().isAfter(created));
    }

    @Test
    void refund_onCreate_setsCreatedTimestampAndLeavesProcessedAtUnset() {
        Refund refund = Refund.builder()
                .refundId("RFD-ABC123")
                .paymentId(1L)
                .amount(new BigDecimal("5.00"))
                .status(PaymentStatus.PROCESSING)
                .build();

        ReflectionTestUtils.invokeMethod(refund, "onCreate");

        assertNotNull(refund.getCreatedAt());
        assertNull(refund.getProcessedAt());
    }

    @Test
    void paymentMethod_onCreate_setsCreatedTimestamp() {
        PaymentMethod method = PaymentMethod.builder()
                .customerId(1L)
                .type(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .cardBrand("VISA")
                .expiryMonth(12)
                .expiryYear(2030)
                .isDefault(true)
                .token("tok_abc")
                .build();

        ReflectionTestUtils.invokeMethod(method, "onCreate");

        assertNotNull(method.getCreatedAt());
        assertEquals("4242", method.getCardLastFour());
        assertEquals(PaymentType.CREDIT_CARD, method.getType());
    }
}
