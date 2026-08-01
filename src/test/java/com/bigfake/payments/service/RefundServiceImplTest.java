package com.bigfake.payments.service;

import com.bigfake.payments.exception.PaymentException;
import com.bigfake.payments.model.dto.RefundRequest;
import com.bigfake.payments.model.entity.Payment;
import com.bigfake.payments.model.entity.Refund;
import com.bigfake.payments.model.enums.PaymentStatus;
import com.bigfake.payments.repository.PaymentRepository;
import com.bigfake.payments.repository.RefundRepository;
import com.bigfake.payments.service.impl.RefundServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RefundServiceImpl.
 */
@ExtendWith(MockitoExtension.class)
class RefundServiceImplTest {

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private RefundServiceImpl refundService;

    private Payment completedPayment;
    private RefundRequest refundRequest;

    @BeforeEach
    void setUp() {
        completedPayment = Payment.builder()
                .id(1L)
                .transactionId("TXN-ORIGINAL1")
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .build();

        refundRequest = RefundRequest.builder()
                .paymentId(1L)
                .amount(new BigDecimal("50.00"))
                .reason("Customer request")
                .initiatedBy("agent-42")
                .build();
    }

    @Test
    void processRefund_success_completesRefundAndMarksPaymentRefunded() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(1L)).thenReturn(Collections.emptyList());
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));

        Refund result = refundService.processRefund(refundRequest);

        assertEquals(PaymentStatus.COMPLETED, result.getStatus());
        assertNotNull(result.getProcessedAt());
        assertTrue(result.getRefundId().startsWith("RFD-"));
        assertEquals(new BigDecimal("50.00"), result.getAmount());
        assertEquals("agent-42", result.getInitiatedBy());
        verify(paymentRepository).save(argThat(p -> p.getStatus() == PaymentStatus.REFUNDED));
        verify(notificationService).sendRefundNotification(any(Refund.class), any(Payment.class));
    }

    @Test
    void processRefund_defaultsInitiatedByToSystem() {
        refundRequest.setInitiatedBy(null);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(1L)).thenReturn(Collections.emptyList());
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));

        Refund result = refundService.processRefund(refundRequest);

        assertEquals("system", result.getInitiatedBy());
    }

    @Test
    void processRefund_paymentNotFound_throws() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.empty());

        PaymentException ex = assertThrows(PaymentException.class,
                () -> refundService.processRefund(refundRequest));
        assertEquals("PAYMENT_NOT_FOUND", ex.getErrorCode());
        verify(refundRepository, never()).save(any());
    }

    @Test
    void processRefund_paymentNotCompleted_throws() {
        completedPayment.setStatus(PaymentStatus.PENDING);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> refundService.processRefund(refundRequest));
        assertEquals("INVALID_REFUND_STATE", ex.getErrorCode());
    }

    @Test
    void processRefund_amountExceedsPayment_throws() {
        refundRequest.setAmount(new BigDecimal("100.01"));
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> refundService.processRefund(refundRequest));
        assertEquals("REFUND_EXCEEDS_PAYMENT", ex.getErrorCode());
    }

    @Test
    void processRefund_totalRefundsWouldExceedPayment_throws() {
        Refund priorRefund = Refund.builder()
                .refundId("RFD-PRIOR")
                .paymentId(1L)
                .amount(new BigDecimal("60.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(1L)).thenReturn(List.of(priorRefund));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> refundService.processRefund(refundRequest));
        assertEquals("REFUND_TOTAL_EXCEEDED", ex.getErrorCode());
    }

    @Test
    void processRefund_ignoresNonCompletedPriorRefundsInTotal() {
        Refund failedRefund = Refund.builder()
                .refundId("RFD-FAILED")
                .paymentId(1L)
                .amount(new BigDecimal("60.00"))
                .status(PaymentStatus.FAILED)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(1L)).thenReturn(List.of(failedRefund));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));

        Refund result = refundService.processRefund(refundRequest);

        assertEquals(PaymentStatus.COMPLETED, result.getStatus());
    }

    @Test
    void processRefund_fullRefundOfExactAmount_isAllowed() {
        refundRequest.setAmount(new BigDecimal("100.00"));
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(1L)).thenReturn(Collections.emptyList());
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));

        Refund result = refundService.processRefund(refundRequest);

        assertEquals(PaymentStatus.COMPLETED, result.getStatus());
    }

    @Test
    void processRefund_notificationFailure_doesNotFailRefund() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(1L)).thenReturn(Collections.emptyList());
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("webhook down"))
                .when(notificationService).sendRefundNotification(any(), any());

        Refund result = refundService.processRefund(refundRequest);

        assertEquals(PaymentStatus.COMPLETED, result.getStatus());
    }

    @Test
    void getRefundById_found_returnsRefund() {
        Refund refund = Refund.builder().id(5L).refundId("RFD-FOUND").build();
        when(refundRepository.findById(5L)).thenReturn(Optional.of(refund));

        assertEquals(refund, refundService.getRefundById(5L));
    }

    @Test
    void getRefundById_notFound_throws() {
        when(refundRepository.findById(99L)).thenReturn(Optional.empty());

        PaymentException ex = assertThrows(PaymentException.class,
                () -> refundService.getRefundById(99L));
        assertEquals("REFUND_NOT_FOUND", ex.getErrorCode());
    }

    @Test
    void getRefundsForPayment_returnsRepositoryResult() {
        Refund refund = Refund.builder().id(1L).paymentId(7L).build();
        when(refundRepository.findByPaymentId(7L)).thenReturn(List.of(refund));

        assertEquals(List.of(refund), refundService.getRefundsForPayment(7L));
    }
}
