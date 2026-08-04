package com.bigfake.payments.model.entity;

import com.bigfake.payments.model.enums.PaymentStatus;
import com.bigfake.payments.model.enums.PaymentType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the JPA lifecycle callbacks on the entities. The callbacks are invoked
 * directly (they are package-visible) so no persistence context is needed.
 */
class EntityLifecycleCallbacksTest {

    private static final LocalDateTime PAST = LocalDateTime.of(2020, 1, 1, 0, 0);

    @Test
    void merchant_onCreateStampsTimestampsAndDefaultsToActive() {
        Merchant merchant = Merchant.builder()
                .merchantCode("MERCH001")
                .businessName("Test Store")
                .build();

        merchant.onCreate();

        assertNotNull(merchant.getCreatedAt());
        assertNotNull(merchant.getUpdatedAt());
        assertFalse(merchant.getUpdatedAt().isBefore(merchant.getCreatedAt()));
        assertTrue(merchant.getIsActive());
    }

    @Test
    void merchant_onCreateKeepsAnExplicitInactiveFlag() {
        Merchant merchant = Merchant.builder()
                .merchantCode("MERCH002")
                .businessName("Closed Store")
                .isActive(false)
                .build();

        merchant.onCreate();

        assertFalse(merchant.getIsActive());
    }

    @Test
    void merchant_onUpdateOnlyRefreshesUpdatedAt() {
        Merchant merchant = Merchant.builder().merchantCode("MERCH003").businessName("Store").build();
        ReflectionTestUtils.setField(merchant, "createdAt", PAST);
        ReflectionTestUtils.setField(merchant, "updatedAt", PAST);

        merchant.onUpdate();

        assertEquals(PAST, merchant.getCreatedAt());
        assertTrue(merchant.getUpdatedAt().isAfter(PAST));
    }

    @Test
    void payment_onCreateStampsBothTimestamps() {
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
        assertFalse(payment.getUpdatedAt().isBefore(payment.getCreatedAt()));
    }

    @Test
    void payment_onUpdateOnlyRefreshesUpdatedAt() {
        Payment payment = Payment.builder().transactionId("TXN-LIFECYCLE").build();
        ReflectionTestUtils.setField(payment, "createdAt", PAST);
        ReflectionTestUtils.setField(payment, "updatedAt", PAST);

        payment.onUpdate();

        assertEquals(PAST, payment.getCreatedAt());
        assertTrue(payment.getUpdatedAt().isAfter(PAST));
    }

    @Test
    void refund_onCreateStampsCreatedAtOnly() {
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
    void paymentMethod_onCreateStampsCreatedAt() {
        PaymentMethod paymentMethod = PaymentMethod.builder()
                .customerId(1L)
                .type(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .build();

        paymentMethod.onCreate();

        assertNotNull(paymentMethod.getCreatedAt());
    }
}
