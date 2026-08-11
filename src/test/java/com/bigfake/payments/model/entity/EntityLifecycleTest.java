package com.bigfake.payments.model.entity;

import com.bigfake.payments.model.enums.PaymentStatus;
import com.bigfake.payments.model.enums.PaymentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for JPA lifecycle callbacks and value semantics of the entities.
 */
class EntityLifecycleTest {

    @Test
    void payment_onCreate_setsCreatedAndUpdatedTimestamps() {
        Payment payment = new Payment();

        payment.onCreate();

        assertNotNull(payment.getCreatedAt());
        assertNotNull(payment.getUpdatedAt());
        assertFalse(payment.getUpdatedAt().isBefore(payment.getCreatedAt()));
    }

    @Test
    void payment_onUpdate_movesUpdatedTimestampForward() {
        Payment payment = new Payment();
        payment.onCreate();
        LocalDateTime originalUpdatedAt = payment.getUpdatedAt();
        payment.setUpdatedAt(originalUpdatedAt.minusMinutes(5));

        payment.onUpdate();

        assertTrue(payment.getUpdatedAt().isAfter(originalUpdatedAt.minusMinutes(5)));
    }

    @Test
    void payment_valueSemantics_areFieldBased() {
        Payment first = Payment.builder()
                .transactionId("TXN-1")
                .merchantId(1L)
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .status(PaymentStatus.PENDING)
                .paymentType(PaymentType.ACH)
                .build();
        Payment second = Payment.builder()
                .transactionId("TXN-1")
                .merchantId(1L)
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .status(PaymentStatus.PENDING)
                .paymentType(PaymentType.ACH)
                .build();

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertTrue(first.toString().contains("TXN-1"));

        second.setStatus(PaymentStatus.FAILED);
        assertNotEquals(first, second);
    }

    @Test
    void merchant_onCreate_setsTimestampsAndDefaultsActive() {
        Merchant merchant = new Merchant();

        merchant.onCreate();

        assertNotNull(merchant.getCreatedAt());
        assertNotNull(merchant.getUpdatedAt());
        assertTrue(merchant.getIsActive());
    }

    @Test
    void merchant_onCreate_keepsExplicitInactiveFlag() {
        Merchant merchant = Merchant.builder()
                .merchantCode("MERCH001")
                .businessName("Test Store")
                .isActive(false)
                .build();

        merchant.onCreate();

        assertFalse(merchant.getIsActive());
    }

    @Test
    void merchant_onUpdate_refreshesUpdatedTimestampOnly() {
        Merchant merchant = new Merchant();
        merchant.onCreate();
        LocalDateTime createdAt = merchant.getCreatedAt();
        merchant.setUpdatedAt(createdAt.minusHours(1));

        merchant.onUpdate();

        assertEquals(createdAt, merchant.getCreatedAt());
        assertTrue(merchant.getUpdatedAt().isAfter(createdAt.minusHours(1)));
    }

    @Test
    void refund_onCreate_setsCreatedTimestamp() {
        Refund refund = Refund.builder()
                .refundId("RFD-1")
                .paymentId(1L)
                .amount(new BigDecimal("5.00"))
                .status(PaymentStatus.PROCESSING)
                .build();

        refund.onCreate();

        assertNotNull(refund.getCreatedAt());
        assertEquals("RFD-1", refund.getRefundId());
        assertEquals(PaymentStatus.PROCESSING, refund.getStatus());
    }

    @Test
    void refund_valueSemantics_areFieldBased() {
        Refund first = Refund.builder().refundId("RFD-1").amount(new BigDecimal("5.00")).build();
        Refund second = Refund.builder().refundId("RFD-1").amount(new BigDecimal("5.00")).build();

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());

        second.setReason("duplicate charge");
        assertNotEquals(first, second);
    }

    @Test
    void paymentMethod_onCreate_setsCreatedTimestamp() {
        PaymentMethod method = PaymentMethod.builder()
                .customerId(3L)
                .type(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .cardBrand("VISA")
                .expiryMonth(12)
                .expiryYear(2030)
                .isDefault(true)
                .token("tok_123")
                .build();

        method.onCreate();

        assertNotNull(method.getCreatedAt());
        assertEquals("4242", method.getCardLastFour());
        assertTrue(method.getIsDefault());
    }

    @Test
    void paymentMethod_valueSemantics_areFieldBased() {
        PaymentMethod first = PaymentMethod.builder().customerId(3L).type(PaymentType.DEBIT).build();
        PaymentMethod second = PaymentMethod.builder().customerId(3L).type(PaymentType.DEBIT).build();

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());

        second.setCardBrand("MASTERCARD");
        assertNotEquals(first, second);
    }
}
