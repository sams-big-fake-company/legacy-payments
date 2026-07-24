package com.bigfake.payments.controller;

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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain unit tests for PaymentController endpoints that are not exercised by
 * the MockMvc-based PaymentControllerTest.
 */
@ExtendWith(MockitoExtension.class)
class PaymentControllerUnitTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentController paymentController;

    private static PaymentResponse response(Long id) {
        return PaymentResponse.builder()
                .id(id)
                .transactionId("TXN-" + id)
                .merchantId(1L)
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();
    }

    @Test
    void createPaymentReturns201() {
        PaymentRequest request = PaymentRequest.builder().merchantId(1L).amount(BigDecimal.TEN)
                .currency("USD").paymentType(PaymentType.WIRE).build();
        PaymentResponse expected = response(1L);
        when(paymentService.processPayment(request)).thenReturn(expected);

        ResponseEntity<PaymentResponse> result = paymentController.createPayment(request);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertSame(expected, result.getBody());
    }

    @Test
    void getPaymentByTransactionIdReturns200() {
        PaymentResponse expected = response(4L);
        when(paymentService.getPaymentByTransactionId("TXN-4")).thenReturn(expected);

        ResponseEntity<PaymentResponse> result = paymentController.getPaymentByTransactionId("TXN-4");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(expected, result.getBody());
    }

    @Test
    void getPaymentsByMerchantReturnsList() {
        List<PaymentResponse> expected = List.of(response(1L), response(2L));
        when(paymentService.getPaymentsByMerchant(1L)).thenReturn(expected);

        ResponseEntity<List<PaymentResponse>> result = paymentController.getPaymentsByMerchant(1L);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(expected, result.getBody());
    }

    @Test
    void updateStatusDelegatesToService() {
        PaymentResponse expected = response(3L);
        when(paymentService.updatePaymentStatus(3L, PaymentStatus.REFUNDED)).thenReturn(expected);

        ResponseEntity<PaymentResponse> result = paymentController.updateStatus(3L, PaymentStatus.REFUNDED);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(expected, result.getBody());
    }

    @Test
    void cancelPaymentReturns204WithoutBody() {
        ResponseEntity<Void> result = paymentController.cancelPayment(7L);

        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        assertNull(result.getBody());
        verify(paymentService).cancelPayment(7L);
    }
}
