package com.bigfake.payments.service;

import com.bigfake.payments.exception.PaymentException;
import com.bigfake.payments.model.dto.RefundRequest;
import com.bigfake.payments.model.entity.Payment;
import com.bigfake.payments.model.entity.Refund;
import com.bigfake.payments.model.enums.PaymentStatus;
import com.bigfake.payments.model.enums.PaymentType;
import com.bigfake.payments.repository.PaymentRepository;
import com.bigfake.payments.repository.RefundRepository;
import com.bigfake.payments.service.impl.RefundServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private RefundRequest request;

    @BeforeEach
    void setUp() {
        completedPayment = Payment.builder()
                .id(1L)
                .transactionId("TXN-ORIGINAL1")
                .merchantId(7L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();

        request = RefundRequest.builder()
                .paymentId(1L)
                .amount(new BigDecimal("40.00"))
                .reason("Customer returned item")
                .initiatedBy("agent-42")
                .build();
    }

    private void stubRefundSaveEchoingArgument() {
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> {
            Refund refund = invocation.getArgument(0);
            refund.setId(99L);
            return refund;
        });
    }

    @Test
    void processRefund_createsCompletedRefundAndMarksPaymentRefunded() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(1L)).thenReturn(Collections.emptyList());
        stubRefundSaveEchoingArgument();

        Refund refund = refundService.processRefund(request);

        assertNotNull(refund.getRefundId());
        assertTrue(refund.getRefundId().startsWith("RFD-"));
        assertEquals(new BigDecimal("40.00"), refund.getAmount());
        assertEquals("Customer returned item", refund.getReason());
        assertEquals("agent-42", refund.getInitiatedBy());
        assertEquals(PaymentStatus.COMPLETED, refund.getStatus());
        assertNotNull(refund.getProcessedAt());
        assertEquals(1L, refund.getPaymentId());

        verify(paymentRepository).save(completedPayment);
        assertEquals(PaymentStatus.REFUNDED, completedPayment.getStatus());
        verify(notificationService).sendRefundNotification(refund, completedPayment);
    }

    @Test
    void processRefund_defaultsInitiatedByToSystem() {
        request.setInitiatedBy(null);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(1L)).thenReturn(Collections.emptyList());
        stubRefundSaveEchoingArgument();

        assertEquals("system", refundService.processRefund(request).getInitiatedBy());
    }

    @Test
    void processRefund_allowsFullRefundOfRemainingBalance() {
        Refund previous = Refund.builder()
                .refundId("RFD-PREVIOUS")
                .paymentId(1L)
                .amount(new BigDecimal("60.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(1L)).thenReturn(List.of(previous));
        stubRefundSaveEchoingArgument();

        assertEquals(PaymentStatus.COMPLETED, refundService.processRefund(request).getStatus());
    }

    @Test
    void processRefund_ignoresNonCompletedRefundsWhenTotallingPreviousRefunds() {
        Refund failedRefund = Refund.builder()
                .refundId("RFD-FAILED")
                .paymentId(1L)
                .amount(new BigDecimal("100.00"))
                .status(PaymentStatus.FAILED)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(1L)).thenReturn(List.of(failedRefund));
        stubRefundSaveEchoingArgument();

        assertEquals(PaymentStatus.COMPLETED, refundService.processRefund(request).getStatus());
    }

    @Test
    void processRefund_paymentNotFound() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class, () -> refundService.processRefund(request));

        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
        verify(refundRepository, never()).save(any(Refund.class));
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"PENDING", "PROCESSING", "FAILED", "REFUNDED"})
    void processRefund_rejectsPaymentsThatAreNotCompleted(PaymentStatus status) {
        completedPayment.setStatus(status);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));

        PaymentException exception = assertThrows(PaymentException.class, () -> refundService.processRefund(request));

        assertEquals("INVALID_REFUND_STATE", exception.getErrorCode());
        verify(refundRepository, never()).save(any(Refund.class));
    }

    @Test
    void processRefund_rejectsAmountAboveOriginalPayment() {
        request.setAmount(new BigDecimal("100.01"));
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));

        PaymentException exception = assertThrows(PaymentException.class, () -> refundService.processRefund(request));

        assertEquals("REFUND_EXCEEDS_PAYMENT", exception.getErrorCode());
        verify(refundRepository, never()).findByPaymentId(anyLong());
    }

    @Test
    void processRefund_rejectsWhenPreviousRefundsWouldBeExceeded() {
        Refund previous = Refund.builder()
                .refundId("RFD-PREVIOUS")
                .paymentId(1L)
                .amount(new BigDecimal("70.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(1L)).thenReturn(List.of(previous));

        PaymentException exception = assertThrows(PaymentException.class, () -> refundService.processRefund(request));

        assertEquals("REFUND_TOTAL_EXCEEDED", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("70.00"));
        verify(refundRepository, never()).save(any(Refund.class));
    }

    @Test
    void processRefund_stillReturnsRefundWhenNotificationFails() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(1L)).thenReturn(Collections.emptyList());
        stubRefundSaveEchoingArgument();
        doThrow(new RuntimeException("webhook down"))
                .when(notificationService).sendRefundNotification(any(Refund.class), any(Payment.class));

        Refund refund = refundService.processRefund(request);

        assertEquals(PaymentStatus.COMPLETED, refund.getStatus());
    }

    @Test
    void processRefund_persistsProcessingStatusBeforeCompletingRefund() {
        List<PaymentStatus> savedStatuses = new ArrayList<>();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(1L)).thenReturn(Collections.emptyList());
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> {
            Refund refund = invocation.getArgument(0);
            savedStatuses.add(refund.getStatus());
            return refund;
        });

        refundService.processRefund(request);

        assertEquals(List.of(PaymentStatus.PROCESSING, PaymentStatus.COMPLETED), savedStatuses);
    }

    @Test
    void getRefundById_returnsRefund() {
        Refund refund = Refund.builder().id(5L).refundId("RFD-FOUND").build();
        when(refundRepository.findById(5L)).thenReturn(Optional.of(refund));

        assertSame(refund, refundService.getRefundById(5L));
    }

    @Test
    void getRefundById_notFound() {
        when(refundRepository.findById(5L)).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class, () -> refundService.getRefundById(5L));

        assertEquals("REFUND_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void getRefundsForPayment_delegatesToRepository() {
        List<Refund> refunds = Arrays.asList(
                Refund.builder().refundId("RFD-1").build(),
                Refund.builder().refundId("RFD-2").build());
        when(refundRepository.findByPaymentId(1L)).thenReturn(refunds);

        assertEquals(refunds, refundService.getRefundsForPayment(1L));
    }
}
