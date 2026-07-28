package com.bigfake.payments.testsupport;

import com.bigfake.payments.model.dto.PaymentRequest;
import com.bigfake.payments.model.dto.RefundRequest;
import com.bigfake.payments.model.entity.Merchant;
import com.bigfake.payments.model.entity.Payment;
import com.bigfake.payments.model.entity.Refund;
import com.bigfake.payments.model.enums.PaymentStatus;
import com.bigfake.payments.model.enums.PaymentType;

import java.math.BigDecimal;

/**
 * Factory methods for test data. Every factory returns a builder so tests can
 * override only the fields relevant to the behaviour under test.
 */
public final class TestFixtures {

    public static final Long MERCHANT_ID = 1L;

    private TestFixtures() {
    }

    public static Merchant.MerchantBuilder merchant() {
        return Merchant.builder()
                .id(MERCHANT_ID)
                .merchantCode("MERCH001")
                .businessName("Test Store")
                .contactEmail("ops@teststore.example")
                .isActive(true)
                .dailyLimit(new BigDecimal("100000.00"));
    }

    public static PaymentRequest.PaymentRequestBuilder creditCardRequest() {
        return PaymentRequest.builder()
                .merchantId(MERCHANT_ID)
                .amount(new BigDecimal("99.99"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .customerEmail("customer@example.com");
    }

    public static Payment.PaymentBuilder payment() {
        return Payment.builder()
                .id(10L)
                .transactionId("TXN-0123456789ABCDEF")
                .merchantId(MERCHANT_ID)
                .amount(new BigDecimal("99.99"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .customerEmail("customer@example.com");
    }

    public static Refund.RefundBuilder refund() {
        return Refund.builder()
                .id(20L)
                .refundId("RFD-ABCDEF123456")
                .paymentId(10L)
                .amount(new BigDecimal("10.00"))
                .status(PaymentStatus.COMPLETED)
                .initiatedBy("support-agent");
    }

    public static RefundRequest.RefundRequestBuilder refundRequest() {
        return RefundRequest.builder()
                .paymentId(10L)
                .amount(new BigDecimal("10.00"))
                .reason("Customer returned the item");
    }
}
