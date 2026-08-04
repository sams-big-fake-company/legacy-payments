package com.bigfake.payments.controller;

import com.bigfake.payments.exception.PaymentException;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RefundController request/response handling.
 */
@ExtendWith(MockitoExtension.class)
class RefundControllerTest {

    @Mock
    private RefundService refundService;

    @InjectMocks
    private RefundController refundController;

    private static Refund refund(long id, String refundId) {
        return Refund.builder()
                .id(id)
                .refundId(refundId)
                .paymentId(1L)
                .amount(new BigDecimal("25.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
    }

    @Test
    void processRefund_returns201WithTheCreatedRefund() {
        RefundRequest request = RefundRequest.builder()
                .paymentId(1L)
                .amount(new BigDecimal("25.00"))
                .reason("Damaged item")
                .build();
        Refund created = refund(1L, "RFD-CREATED1");
        when(refundService.processRefund(request)).thenReturn(created);

        ResponseEntity<Refund> response = refundController.processRefund(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(created, response.getBody());
    }

    @Test
    void getRefund_returns200WithTheRefund() {
        Refund existing = refund(5L, "RFD-EXISTING");
        when(refundService.getRefundById(5L)).thenReturn(existing);

        ResponseEntity<Refund> response = refundController.getRefund(5L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(existing, response.getBody());
    }

    @Test
    void getRefund_propagatesNotFound() {
        when(refundService.getRefundById(404L))
                .thenThrow(new PaymentException("Refund not found: 404", "REFUND_NOT_FOUND"));

        PaymentException exception = assertThrows(PaymentException.class, () -> refundController.getRefund(404L));

        assertEquals("REFUND_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void getRefundsForPayment_returnsAllRefunds() {
        List<Refund> refunds = Arrays.asList(refund(1L, "RFD-1"), refund(2L, "RFD-2"));
        when(refundService.getRefundsForPayment(1L)).thenReturn(refunds);

        ResponseEntity<List<Refund>> response = refundController.getRefundsForPayment(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(refunds, response.getBody());
    }

    @Test
    void getRefundsForPayment_returnsEmptyListWhenThereAreNoRefunds() {
        when(refundService.getRefundsForPayment(2L)).thenReturn(Collections.emptyList());

        assertEquals(Collections.emptyList(), refundController.getRefundsForPayment(2L).getBody());
    }
}
