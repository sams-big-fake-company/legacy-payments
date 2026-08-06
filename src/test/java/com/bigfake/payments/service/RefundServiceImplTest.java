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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RefundServiceImpl (PAY-3455).
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
                .id(10L)
                .transactionId("TXN-ORIGINAL01")
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();

        refundRequest = RefundRequest.builder()
                .paymentId(10L)
                .amount(new BigDecimal("40.00"))
                .reason("Customer changed their mind")
                .initiatedBy("support-agent")
                .build();
    }

    private void stubRefundSave() {
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> {
            Refund refund = invocation.getArgument(0);
            refund.setId(99L);
            return refund;
        });
    }

    @Test
    void processRefund_throwsWhenPaymentDoesNotExist() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class,
                () -> refundService.processRefund(refundRequest));

        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
        verify(refundRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"PENDING", "PROCESSING", "FAILED", "REFUNDED"})
    void processRefund_throwsWhenPaymentIsNotCompleted(PaymentStatus status) {
        completedPayment.setStatus(status);
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(completedPayment));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> refundService.processRefund(refundRequest));

        assertEquals("INVALID_REFUND_STATE", exception.getErrorCode());
        verify(refundRepository, never()).save(any());
    }

    @Test
    void processRefund_throwsWhenAmountExceedsOriginalPayment() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(completedPayment));
        refundRequest.setAmount(new BigDecimal("100.01"));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> refundService.processRefund(refundRequest));

        assertEquals("REFUND_EXCEEDS_PAYMENT", exception.getErrorCode());
    }

    @Test
    void processRefund_throwsWhenPriorRefundsWouldBeExceeded() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(10L)).thenReturn(List.of(
                Refund.builder().amount(new BigDecimal("70.00")).status(PaymentStatus.COMPLETED).build()));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> refundService.processRefund(refundRequest));

        assertEquals("REFUND_TOTAL_EXCEEDED", exception.getErrorCode());
        verify(refundRepository, never()).save(any());
    }

    @Test
    void processRefund_ignoresNonCompletedPriorRefundsWhenTotalling() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(10L)).thenReturn(List.of(
                Refund.builder().amount(new BigDecimal("70.00")).status(PaymentStatus.FAILED).build(),
                Refund.builder().amount(new BigDecimal("70.00")).status(PaymentStatus.PROCESSING).build()));
        stubRefundSave();

        Refund refund = refundService.processRefund(refundRequest);

        assertEquals(PaymentStatus.COMPLETED, refund.getStatus());
    }

    @Test
    void processRefund_createsCompletedRefundAndMarksPaymentRefunded() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(10L)).thenReturn(Collections.emptyList());
        stubRefundSave();

        Refund refund = refundService.processRefund(refundRequest);

        assertNotNull(refund.getRefundId());
        assertTrue(refund.getRefundId().startsWith("RFD-"), "refundId should be prefixed with RFD-");
        assertEquals(10L, refund.getPaymentId());
        assertEquals(new BigDecimal("40.00"), refund.getAmount());
        assertEquals("Customer changed their mind", refund.getReason());
        assertEquals("support-agent", refund.getInitiatedBy());
        assertEquals(PaymentStatus.COMPLETED, refund.getStatus());
        assertNotNull(refund.getProcessedAt());

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertEquals(PaymentStatus.REFUNDED, paymentCaptor.getValue().getStatus());
    }

    @Test
    void processRefund_defaultsInitiatedByToSystem() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(10L)).thenReturn(Collections.emptyList());
        stubRefundSave();
        refundRequest.setInitiatedBy(null);

        Refund refund = refundService.processRefund(refundRequest);

        assertEquals("system", refund.getInitiatedBy());
    }

    @Test
    void processRefund_allowsRefundingTheFullRemainingBalance() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(10L)).thenReturn(List.of(
                Refund.builder().amount(new BigDecimal("60.00")).status(PaymentStatus.COMPLETED).build()));
        stubRefundSave();

        Refund refund = refundService.processRefund(refundRequest);

        assertEquals(PaymentStatus.COMPLETED, refund.getStatus());
    }

    @Test
    void processRefund_notifiesWithRefundAndOriginalPayment() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(10L)).thenReturn(Collections.emptyList());
        stubRefundSave();

        Refund refund = refundService.processRefund(refundRequest);

        verify(notificationService).sendRefundNotification(refund, completedPayment);
    }

    @Test
    void processRefund_succeedsEvenWhenNotificationFails() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(10L)).thenReturn(Collections.emptyList());
        stubRefundSave();
        doThrow(new IllegalStateException("notification backend down"))
                .when(notificationService).sendRefundNotification(any(), any());

        Refund refund = refundService.processRefund(refundRequest);

        assertEquals(PaymentStatus.COMPLETED, refund.getStatus());
        verify(refundRepository, atLeastOnce()).save(any(Refund.class));
    }

    @Test
    void getRefundById_returnsRefund() {
        Refund refund = Refund.builder().id(5L).refundId("RFD-ABC").build();
        when(refundRepository.findById(5L)).thenReturn(Optional.of(refund));

        assertSame(refund, refundService.getRefundById(5L));
    }

    @Test
    void getRefundById_throwsWhenMissing() {
        when(refundRepository.findById(404L)).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class,
                () -> refundService.getRefundById(404L));

        assertEquals("REFUND_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void getRefundsForPayment_delegatesToRepository() {
        List<Refund> refunds = List.of(Refund.builder().id(1L).build(), Refund.builder().id(2L).build());
        when(refundRepository.findByPaymentId(10L)).thenReturn(refunds);

        assertEquals(refunds, refundService.getRefundsForPayment(10L));
    }
}
