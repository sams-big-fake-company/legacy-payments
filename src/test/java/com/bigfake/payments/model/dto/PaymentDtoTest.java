package com.bigfake.payments.model.dto;

import com.bigfake.payments.model.enums.PaymentStatus;
import com.bigfake.payments.model.enums.PaymentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the request/response DTOs.
 */
class PaymentDtoTest {

    @Test
    void paymentRequestBuilderPopulatesAllProvidedFields() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("20.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .description("order 123")
                .customerEmail("a@b.com")
                .customerName("A B")
                .cardLastFour("4242")
                .idempotencyKey("idem-1")
                .metadata("{\"order\":123}")
                .build();

        assertEquals(1L, request.getMerchantId());
        assertEquals(new BigDecimal("20.00"), request.getAmount());
        assertEquals("USD", request.getCurrency());
        assertEquals(PaymentType.CREDIT_CARD, request.getPaymentType());
        assertEquals("order 123", request.getDescription());
        assertEquals("a@b.com", request.getCustomerEmail());
        assertEquals("A B", request.getCustomerName());
        assertEquals("4242", request.getCardLastFour());
        assertEquals("idem-1", request.getIdempotencyKey());
        assertEquals("{\"order\":123}", request.getMetadata());
    }

    @Test
    void paymentRequestNoArgsConstructorLeavesFieldsUnset() {
        PaymentRequest request = new PaymentRequest();

        assertNull(request.getMerchantId());
        assertNull(request.getAmount());
        assertNull(request.getCurrency());
        assertNull(request.getPaymentType());
    }

    @Test
    void paymentRequestSettersUpdateFields() {
        PaymentRequest request = new PaymentRequest();
        request.setMerchantId(2L);
        request.setAmount(new BigDecimal("5.00"));
        request.setCurrency("EUR");
        request.setPaymentType(PaymentType.ACH);
        request.setIdempotencyKey("idem-2");

        assertEquals(2L, request.getMerchantId());
        assertEquals(new BigDecimal("5.00"), request.getAmount());
        assertEquals("EUR", request.getCurrency());
        assertEquals(PaymentType.ACH, request.getPaymentType());
        assertEquals("idem-2", request.getIdempotencyKey());
    }

    @Test
    void paymentRequestsWithSameFieldsAreEqual() {
        PaymentRequest first = PaymentRequest.builder().merchantId(1L).currency("USD").build();
        PaymentRequest second = PaymentRequest.builder().merchantId(1L).currency("USD").build();

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, PaymentRequest.builder().merchantId(2L).currency("USD").build());
        assertTrue(first.toString().contains("USD"));
    }

    @Test
    void paymentResponseBuilderPopulatesAllProvidedFields() {
        LocalDateTime created = LocalDateTime.of(2024, 1, 1, 8, 0);
        LocalDateTime completed = created.plusMinutes(1);

        PaymentResponse response = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-1")
                .merchantId(2L)
                .amount(new BigDecimal("30.00"))
                .currency("GBP")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.WIRE)
                .description("wire payment")
                .customerEmail("a@b.com")
                .feeAmount(new BigDecimal("0.03"))
                .netAmount(new BigDecimal("29.97"))
                .failureReason(null)
                .gatewayReference("GW-1")
                .createdAt(created)
                .completedAt(completed)
                .build();

        assertEquals(1L, response.getId());
        assertEquals("TXN-1", response.getTransactionId());
        assertEquals(PaymentStatus.COMPLETED, response.getStatus());
        assertEquals(PaymentType.WIRE, response.getPaymentType());
        assertEquals(new BigDecimal("29.97"), response.getNetAmount());
        assertNull(response.getFailureReason());
        assertEquals(created, response.getCreatedAt());
        assertEquals(completed, response.getCompletedAt());
    }

    @Test
    void paymentResponseSettersUpdateFields() {
        PaymentResponse response = new PaymentResponse();
        response.setStatus(PaymentStatus.FAILED);
        response.setFailureReason("declined");

        assertEquals(PaymentStatus.FAILED, response.getStatus());
        assertEquals("declined", response.getFailureReason());
    }

    @Test
    void refundRequestBuilderPopulatesAllProvidedFields() {
        RefundRequest request = RefundRequest.builder()
                .paymentId(1L)
                .amount(new BigDecimal("7.50"))
                .reason("item returned")
                .initiatedBy("agent-1")
                .build();

        assertEquals(1L, request.getPaymentId());
        assertEquals(new BigDecimal("7.50"), request.getAmount());
        assertEquals("item returned", request.getReason());
        assertEquals("agent-1", request.getInitiatedBy());
        assertEquals(request, RefundRequest.builder()
                .paymentId(1L)
                .amount(new BigDecimal("7.50"))
                .reason("item returned")
                .initiatedBy("agent-1")
                .build());
    }

    @Test
    void refundRequestSettersUpdateFields() {
        RefundRequest request = new RefundRequest();
        request.setPaymentId(9L);
        request.setAmount(new BigDecimal("1.00"));
        request.setReason("duplicate");
        request.setInitiatedBy("system");

        assertEquals(9L, request.getPaymentId());
        assertEquals(new BigDecimal("1.00"), request.getAmount());
        assertEquals("duplicate", request.getReason());
        assertEquals("system", request.getInitiatedBy());
        assertTrue(request.toString().contains("duplicate"));
    }
}
