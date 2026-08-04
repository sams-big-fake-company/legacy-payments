package com.bigfake.payments.controller;

import com.bigfake.payments.exception.PaymentException;
import com.bigfake.payments.model.dto.PaymentRequest;
import com.bigfake.payments.model.dto.PaymentResponse;
import com.bigfake.payments.model.enums.PaymentStatus;
import com.bigfake.payments.model.enums.PaymentType;
import com.bigfake.payments.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the PaymentController endpoints that the MockMvc slice test does not cover.
 */
@ExtendWith(MockitoExtension.class)
class PaymentControllerUnitTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentController paymentController;

    private static PaymentResponse response(long id, String transactionId, PaymentStatus status) {
        return PaymentResponse.builder()
                .id(id)
                .transactionId(transactionId)
                .merchantId(1L)
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .status(status)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();
    }

    @Test
    void createPayment_returns201() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .build();
        PaymentResponse created = response(1L, "TXN-CREATED", PaymentStatus.COMPLETED);
        when(paymentService.processPayment(request)).thenReturn(created);

        ResponseEntity<PaymentResponse> result = paymentController.createPayment(request);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertSame(created, result.getBody());
    }

    @Test
    void getPaymentByTransactionId_returns200() {
        PaymentResponse payment = response(2L, "TXN-LOOKUP", PaymentStatus.COMPLETED);
        when(paymentService.getPaymentByTransactionId("TXN-LOOKUP")).thenReturn(payment);

        ResponseEntity<PaymentResponse> result = paymentController.getPaymentByTransactionId("TXN-LOOKUP");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(payment, result.getBody());
    }

    @Test
    void getPaymentByTransactionId_propagatesNotFound() {
        when(paymentService.getPaymentByTransactionId("TXN-MISSING"))
                .thenThrow(new PaymentException("Payment not found: TXN-MISSING", "PAYMENT_NOT_FOUND"));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentController.getPaymentByTransactionId("TXN-MISSING"));

        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void getPaymentsByMerchant_returnsAllPayments() {
        List<PaymentResponse> payments = Arrays.asList(
                response(1L, "TXN-1", PaymentStatus.COMPLETED),
                response(2L, "TXN-2", PaymentStatus.FAILED));
        when(paymentService.getPaymentsByMerchant(1L)).thenReturn(payments);

        ResponseEntity<List<PaymentResponse>> result = paymentController.getPaymentsByMerchant(1L);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(payments, result.getBody());
    }

    @Test
    void getPaymentsByMerchant_returnsEmptyListForAMerchantWithoutPayments() {
        when(paymentService.getPaymentsByMerchant(7L)).thenReturn(Collections.emptyList());

        assertEquals(Collections.emptyList(), paymentController.getPaymentsByMerchant(7L).getBody());
    }

    @Test
    void updateStatus_returns200WithUpdatedPayment() {
        PaymentResponse updated = response(3L, "TXN-UPDATE", PaymentStatus.REFUNDED);
        when(paymentService.updatePaymentStatus(3L, PaymentStatus.REFUNDED)).thenReturn(updated);

        ResponseEntity<PaymentResponse> result = paymentController.updateStatus(3L, PaymentStatus.REFUNDED);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(PaymentStatus.REFUNDED, result.getBody().getStatus());
    }

    @Test
    void cancelPayment_returns204() {
        ResponseEntity<Void> result = paymentController.cancelPayment(4L);

        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        assertNull(result.getBody());
        verify(paymentService).cancelPayment(4L);
    }
}
