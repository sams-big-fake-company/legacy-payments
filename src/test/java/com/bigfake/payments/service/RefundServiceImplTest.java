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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
                .id(7L)
                .transactionId("TXN-ORIGINAL1")
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();

        request = RefundRequest.builder()
                .paymentId(7L)
                .amount(new BigDecimal("40.00"))
                .reason("Customer returned item")
                .initiatedBy("support-agent")
                .build();
    }

    private void stubSaveEchoingRefund() {
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void processRefund_success_completesRefundAndMarksPaymentRefunded() {
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(7L)).thenReturn(Collections.emptyList());
        stubSaveEchoingRefund();

        Refund refund = refundService.processRefund(request);

        assertEquals(PaymentStatus.COMPLETED, refund.getStatus());
        assertEquals(new BigDecimal("40.00"), refund.getAmount());
        assertEquals(7L, refund.getPaymentId());
        assertEquals("Customer returned item", refund.getReason());
        assertEquals("support-agent", refund.getInitiatedBy());
        assertTrue(refund.getRefundId().startsWith("RFD-"));
        assertNotNull(refund.getProcessedAt());
        assertEquals(PaymentStatus.REFUNDED, completedPayment.getStatus());
        verify(paymentRepository).save(completedPayment);
        verify(notificationService).sendRefundNotification(refund, completedPayment);
    }

    @Test
    void processRefund_generatesUniqueRefundIds() {
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(7L)).thenReturn(Collections.emptyList());
        stubSaveEchoingRefund();

        String first = refundService.processRefund(request).getRefundId();
        completedPayment.setStatus(PaymentStatus.COMPLETED);
        String second = refundService.processRefund(request).getRefundId();

        assertEquals(16, first.length());
        assertNotEquals(first, second);
    }

    @Test
    void processRefund_defaultsInitiatedByToSystem() {
        request.setInitiatedBy(null);
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(7L)).thenReturn(Collections.emptyList());
        stubSaveEchoingRefund();

        assertEquals("system", refundService.processRefund(request).getInitiatedBy());
    }

    @Test
    void processRefund_fullAmountIsAllowed() {
        request.setAmount(new BigDecimal("100.00"));
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(7L)).thenReturn(Collections.emptyList());
        stubSaveEchoingRefund();

        assertEquals(PaymentStatus.COMPLETED, refundService.processRefund(request).getStatus());
    }

    @Test
    void processRefund_paymentNotFound_throws() {
        when(paymentRepository.findById(7L)).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class, () -> refundService.processRefund(request));

        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
        verify(refundRepository, never()).save(any(Refund.class));
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"PENDING", "PROCESSING", "FAILED", "REFUNDED"})
    void processRefund_nonCompletedPayment_throws(PaymentStatus status) {
        completedPayment.setStatus(status);
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(completedPayment));

        PaymentException exception = assertThrows(PaymentException.class, () -> refundService.processRefund(request));

        assertEquals("INVALID_REFUND_STATE", exception.getErrorCode());
        verify(refundRepository, never()).save(any(Refund.class));
    }

    @Test
    void processRefund_amountExceedsPayment_throws() {
        request.setAmount(new BigDecimal("100.01"));
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(completedPayment));

        PaymentException exception = assertThrows(PaymentException.class, () -> refundService.processRefund(request));

        assertEquals("REFUND_EXCEEDS_PAYMENT", exception.getErrorCode());
    }

    @Test
    void processRefund_totalOfPriorRefundsWouldBeExceeded_throws() {
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(7L)).thenReturn(List.of(
                Refund.builder().amount(new BigDecimal("70.00")).status(PaymentStatus.COMPLETED).build()));

        PaymentException exception = assertThrows(PaymentException.class, () -> refundService.processRefund(request));

        assertEquals("REFUND_TOTAL_EXCEEDED", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("70.00"));
    }

    @Test
    void processRefund_ignoresNonCompletedPriorRefundsWhenSummingTotals() {
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(7L)).thenReturn(List.of(
                Refund.builder().amount(new BigDecimal("70.00")).status(PaymentStatus.FAILED).build(),
                Refund.builder().amount(new BigDecimal("30.00")).status(PaymentStatus.COMPLETED).build()));
        stubSaveEchoingRefund();

        Refund refund = refundService.processRefund(request);

        assertEquals(PaymentStatus.COMPLETED, refund.getStatus());
    }

    @Test
    void processRefund_gatewayStageFailure_marksRefundFailedAndLeavesPaymentUntouched() {
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(7L)).thenReturn(Collections.emptyList());
        stubSaveEchoingRefund();
        when(paymentRepository.save(completedPayment)).thenThrow(new IllegalStateException("db down"));

        Refund refund = refundService.processRefund(request);

        assertEquals(PaymentStatus.FAILED, refund.getStatus());
        ArgumentCaptor<Refund> saved = ArgumentCaptor.forClass(Refund.class);
        verify(refundRepository, times(2)).save(saved.capture());
        assertEquals(PaymentStatus.FAILED, saved.getValue().getStatus());
    }

    @Test
    void processRefund_notificationFailure_doesNotFailTheRefund() {
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(7L)).thenReturn(Collections.emptyList());
        stubSaveEchoingRefund();
        doThrow(new IllegalStateException("webhook down"))
                .when(notificationService).sendRefundNotification(any(Refund.class), any(Payment.class));

        Refund refund = refundService.processRefund(request);

        assertEquals(PaymentStatus.COMPLETED, refund.getStatus());
    }

    @Test
    void getRefundById_returnsRefund() {
        Refund refund = Refund.builder().id(3L).refundId("RFD-ABC").build();
        when(refundRepository.findById(3L)).thenReturn(Optional.of(refund));

        assertEquals(refund, refundService.getRefundById(3L));
    }

    @Test
    void getRefundById_notFound_throws() {
        when(refundRepository.findById(404L)).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class, () -> refundService.getRefundById(404L));

        assertEquals("REFUND_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void getRefundsForPayment_delegatesToRepository() {
        List<Refund> refunds = List.of(Refund.builder().id(1L).build());
        when(refundRepository.findByPaymentId(7L)).thenReturn(refunds);

        assertEquals(refunds, refundService.getRefundsForPayment(7L));
    }
}
