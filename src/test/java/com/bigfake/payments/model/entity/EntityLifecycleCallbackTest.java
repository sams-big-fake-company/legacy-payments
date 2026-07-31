package com.bigfake.payments.model.entity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the JPA lifecycle callbacks on the entities. These are hand-written
 * (not Lombok-generated) and set audit timestamps and defaults before persistence.
 */
class EntityLifecycleCallbackTest {

    @Test
    void payment_onCreateSetsAuditTimestamps() {
        Payment payment = Payment.builder().transactionId("TXN-LIFECYCLE").build();

        ReflectionTestUtils.invokeMethod(payment, "onCreate");

        assertNotNull(payment.getCreatedAt());
        assertNotNull(payment.getUpdatedAt());
    }

    @Test
    void payment_onUpdateAdvancesUpdatedAtOnly() {
        LocalDateTime originalCreatedAt = LocalDateTime.of(2020, 1, 1, 0, 0);
        Payment payment = Payment.builder()
                .transactionId("TXN-LIFECYCLE")
                .createdAt(originalCreatedAt)
                .updatedAt(originalCreatedAt)
                .build();

        ReflectionTestUtils.invokeMethod(payment, "onUpdate");

        assertEquals(originalCreatedAt, payment.getCreatedAt());
        assertTrue(payment.getUpdatedAt().isAfter(originalCreatedAt));
    }

    @Test
    void merchant_onCreateDefaultsToActive() {
        Merchant merchant = Merchant.builder().merchantCode("MERCH100").build();

        ReflectionTestUtils.invokeMethod(merchant, "onCreate");

        assertEquals(Boolean.TRUE, merchant.getIsActive());
        assertNotNull(merchant.getCreatedAt());
        assertNotNull(merchant.getUpdatedAt());
    }

    @Test
    void merchant_onCreateKeepsExplicitInactiveFlag() {
        Merchant merchant = Merchant.builder().merchantCode("MERCH100").isActive(false).build();

        ReflectionTestUtils.invokeMethod(merchant, "onCreate");

        assertEquals(Boolean.FALSE, merchant.getIsActive());
    }

    @Test
    void merchant_onUpdateRefreshesUpdatedAt() {
        LocalDateTime original = LocalDateTime.of(2020, 1, 1, 0, 0);
        Merchant merchant = Merchant.builder().merchantCode("MERCH100").updatedAt(original).build();

        ReflectionTestUtils.invokeMethod(merchant, "onUpdate");

        assertTrue(merchant.getUpdatedAt().isAfter(original));
    }

    @Test
    void refund_onCreateSetsCreatedAt() {
        Refund refund = Refund.builder().refundId("RFD-LIFECYCLE").build();

        ReflectionTestUtils.invokeMethod(refund, "onCreate");

        assertNotNull(refund.getCreatedAt());
    }

    @Test
    void paymentMethod_onCreateSetsCreatedAt() {
        PaymentMethod paymentMethod = PaymentMethod.builder().customerId(1L).build();

        ReflectionTestUtils.invokeMethod(paymentMethod, "onCreate");

        assertNotNull(paymentMethod.getCreatedAt());
    }
}
