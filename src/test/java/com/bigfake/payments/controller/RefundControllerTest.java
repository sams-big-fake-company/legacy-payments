package com.bigfake.payments.controller;

import com.bigfake.payments.model.dto.RefundRequest;
import com.bigfake.payments.model.entity.Refund;
import com.bigfake.payments.model.enums.PaymentStatus;
import com.bigfake.payments.service.RefundService;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RefundController.
 */
@ExtendWith(MockitoExtension.class)
class RefundControllerTest {

    @Mock
    private RefundService refundService;

    @InjectMocks
    private RefundController refundController;

    @Test
    void processRefundReturns201WithCreatedRefund() {
        RefundRequest request = RefundRequest.builder()
                .paymentId(1L)
                .amount(new BigDecimal("15.00"))
                .reason("duplicate charge")
                .build();
        Refund refund = Refund.builder()
                .id(2L)
                .refundId("RFD-123")
                .paymentId(1L)
                .amount(new BigDecimal("15.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        when(refundService.processRefund(request)).thenReturn(refund);

        ResponseEntity<Refund> response = refundController.processRefund(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(refund, response.getBody());
        verify(refundService).processRefund(request);
    }

    @Test
    void getRefundReturns200() {
        Refund refund = Refund.builder().id(2L).refundId("RFD-123").build();
        when(refundService.getRefundById(2L)).thenReturn(refund);

        ResponseEntity<Refund> response = refundController.getRefund(2L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(refund, response.getBody());
    }

    @Test
    void getRefundsForPaymentReturnsList() {
        List<Refund> refunds = List.of(Refund.builder().id(2L).build(), Refund.builder().id(3L).build());
        when(refundService.getRefundsForPayment(1L)).thenReturn(refunds);

        ResponseEntity<List<Refund>> response = refundController.getRefundsForPayment(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(refunds, response.getBody());
    }

    @Test
    void getRefundsForPaymentReturnsEmptyListWhenNoneExist() {
        when(refundService.getRefundsForPayment(1L)).thenReturn(List.of());

        ResponseEntity<List<Refund>> response = refundController.getRefundsForPayment(1L);

        assertEquals(List.of(), response.getBody());
    }
}
