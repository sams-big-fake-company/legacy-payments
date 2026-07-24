package com.bigfake.payments.model.entity;

import com.bigfake.payments.model.enums.PaymentStatus;
import com.bigfake.payments.model.enums.PaymentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for JPA lifecycle callbacks and value semantics of the entities.
 * The callbacks are package-private/protected, so this test lives in the entity package.
 */
class EntityLifecycleTest {

    @Test
    void paymentOnCreateStampsCreatedAndUpdatedTimestamps() {
        Payment payment = new Payment();

        payment.onCreate();

        assertNotNull(payment.getCreatedAt());
        assertNotNull(payment.getUpdatedAt());
    }

    @Test
    void paymentOnUpdateOnlyStampsUpdatedTimestamp() {
        Payment payment = new Payment();
        LocalDateTime original = LocalDateTime.of(2020, 1, 1, 0, 0);
        payment.setCreatedAt(original);
        payment.setUpdatedAt(original);

        payment.onUpdate();

        assertEquals(original, payment.getCreatedAt());
        assertNotEquals(original, payment.getUpdatedAt());
    }

    @Test
    void merchantOnCreateDefaultsIsActiveToTrue() {
        Merchant merchant = new Merchant();

        merchant.onCreate();

        assertTrue(merchant.getIsActive());
        assertNotNull(merchant.getCreatedAt());
        assertNotNull(merchant.getUpdatedAt());
    }

    @Test
    void merchantOnCreateKeepsExplicitIsActiveFlag() {
        Merchant merchant = new Merchant();
        merchant.setIsActive(false);

        merchant.onCreate();

        assertEquals(false, merchant.getIsActive());
    }

    @Test
    void merchantOnUpdateRefreshesUpdatedTimestamp() {
        Merchant merchant = new Merchant();
        LocalDateTime original = LocalDateTime.of(2021, 6, 1, 12, 0);
        merchant.setUpdatedAt(original);

        merchant.onUpdate();

        assertNotEquals(original, merchant.getUpdatedAt());
    }

    @Test
    void refundOnCreateStampsCreatedTimestamp() {
        Refund refund = new Refund();

        refund.onCreate();

        assertNotNull(refund.getCreatedAt());
        assertNull(refund.getProcessedAt());
    }

    @Test
    void paymentMethodOnCreateStampsCreatedTimestamp() {
        PaymentMethod paymentMethod = new PaymentMethod();

        paymentMethod.onCreate();

        assertNotNull(paymentMethod.getCreatedAt());
    }

    @Test
    void paymentBuilderPopulatesAllProvidedFields() {
        LocalDateTime now = LocalDateTime.of(2024, 3, 1, 9, 30);
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-1")
                .merchantId(2L)
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .status(PaymentStatus.PENDING)
                .paymentType(PaymentType.ACH)
                .description("desc")
                .customerEmail("a@b.com")
                .customerName("A B")
                .cardLastFour("4242")
                .feeAmount(new BigDecimal("0.08"))
                .netAmount(new BigDecimal("9.92"))
                .failureReason(null)
                .gatewayReference("GW-1")
                .idempotencyKey("idem")
                .metadata("{}")
                .createdAt(now)
                .updatedAt(now)
                .completedAt(now)
                .build();

        assertEquals("TXN-1", payment.getTransactionId());
        assertEquals(2L, payment.getMerchantId());
        assertEquals(new BigDecimal("9.92"), payment.getNetAmount());
        assertEquals(PaymentType.ACH, payment.getPaymentType());
        assertEquals("idem", payment.getIdempotencyKey());
        assertEquals(now, payment.getCompletedAt());
    }

    @Test
    void paymentsWithIdenticalFieldsAreEqual() {
        Payment first = Payment.builder().id(1L).transactionId("TXN-1").amount(BigDecimal.TEN).build();
        Payment second = Payment.builder().id(1L).transactionId("TXN-1").amount(BigDecimal.TEN).build();

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertTrue(first.toString().contains("TXN-1"));
    }

    @Test
    void paymentsWithDifferentFieldsAreNotEqual() {
        Payment first = Payment.builder().id(1L).transactionId("TXN-1").build();
        Payment second = Payment.builder().id(2L).transactionId("TXN-2").build();

        assertNotEquals(first, second);
        assertNotEquals(first, new Object());
    }

    @Test
    void merchantBuilderPopulatesLimitsAndWebhook() {
        Merchant merchant = Merchant.builder()
                .id(1L)
                .merchantCode("MERCH001")
                .businessName("Test Store")
                .contactEmail("ops@store.com")
                .isActive(true)
                .dailyLimit(new BigDecimal("1000.00"))
                .monthlyLimit(new BigDecimal("10000.00"))
                .webhookUrl("https://store.com/hook")
                .apiKeyHash("hash")
                .build();

        assertEquals("MERCH001", merchant.getMerchantCode());
        assertEquals(new BigDecimal("1000.00"), merchant.getDailyLimit());
        assertEquals(new BigDecimal("10000.00"), merchant.getMonthlyLimit());
        assertEquals("https://store.com/hook", merchant.getWebhookUrl());
        assertTrue(merchant.toString().contains("MERCH001"));
    }

    @Test
    void refundBuilderPopulatesAllProvidedFields() {
        LocalDateTime now = LocalDateTime.of(2024, 5, 5, 10, 0);
        Refund refund = Refund.builder()
                .id(1L)
                .refundId("RFD-1")
                .paymentId(2L)
                .amount(new BigDecimal("5.00"))
                .reason("duplicate")
                .status(PaymentStatus.COMPLETED)
                .initiatedBy("agent")
                .createdAt(now)
                .processedAt(now)
                .build();

        assertEquals("RFD-1", refund.getRefundId());
        assertEquals(PaymentStatus.COMPLETED, refund.getStatus());
        assertEquals("agent", refund.getInitiatedBy());
        assertEquals(refund, Refund.builder()
                .id(1L)
                .refundId("RFD-1")
                .paymentId(2L)
                .amount(new BigDecimal("5.00"))
                .reason("duplicate")
                .status(PaymentStatus.COMPLETED)
                .initiatedBy("agent")
                .createdAt(now)
                .processedAt(now)
                .build());
    }

    @Test
    void paymentMethodBuilderPopulatesCardDetails() {
        PaymentMethod method = PaymentMethod.builder()
                .id(1L)
                .customerId(3L)
                .type(PaymentType.DEBIT)
                .cardLastFour("1234")
                .cardBrand("VISA")
                .expiryMonth(12)
                .expiryYear(2030)
                .isDefault(true)
                .token("tok_123")
                .build();

        assertEquals(3L, method.getCustomerId());
        assertEquals(PaymentType.DEBIT, method.getType());
        assertEquals("VISA", method.getCardBrand());
        assertEquals(12, method.getExpiryMonth());
        assertEquals(2030, method.getExpiryYear());
        assertTrue(method.getIsDefault());
        assertEquals("tok_123", method.getToken());
    }

    @Test
    void settersUpdateEntityState() {
        Payment payment = new Payment();
        payment.setStatus(PaymentStatus.PROCESSING);
        payment.setFailureReason("gateway timeout");
        payment.setGatewayReference("GW-9");

        assertEquals(PaymentStatus.PROCESSING, payment.getStatus());
        assertEquals("gateway timeout", payment.getFailureReason());
        assertEquals("GW-9", payment.getGatewayReference());
    }
}
