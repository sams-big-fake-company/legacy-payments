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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
                .id(10L)
                .transactionId("TXN-ORIGINAL")
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();

        request = RefundRequest.builder()
                .paymentId(10L)
                .amount(new BigDecimal("40.00"))
                .reason("Customer changed their mind")
                .initiatedBy("agent-7")
                .build();
    }

    private void stubRefundSave() {
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void processRefundCreatesCompletedRefundAndRefundsOriginalPayment() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(10L)).thenReturn(Collections.emptyList());
        stubRefundSave();

        Refund refund = refundService.processRefund(request);

        assertEquals(PaymentStatus.COMPLETED, refund.getStatus());
        assertEquals(new BigDecimal("40.00"), refund.getAmount());
        assertEquals(10L, refund.getPaymentId());
        assertEquals("Customer changed their mind", refund.getReason());
        assertEquals("agent-7", refund.getInitiatedBy());
        assertNotNull(refund.getProcessedAt());
        assertTrue(refund.getRefundId().startsWith("RFD-"));
        assertEquals(PaymentStatus.REFUNDED, completedPayment.getStatus());
        verify(paymentRepository).save(completedPayment);
        verify(notificationService).sendRefundNotification(refund, completedPayment);
    }

    @Test
    void processRefundDefaultsInitiatedByToSystem() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(10L)).thenReturn(Collections.emptyList());
        stubRefundSave();

        Refund refund = refundService.processRefund(
                RefundRequest.builder().paymentId(10L).amount(new BigDecimal("10.00")).build());

        assertEquals("system", refund.getInitiatedBy());
    }

    @Test
    void processRefundPersistsRefundBeforeAndAfterGatewayCall() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(10L)).thenReturn(Collections.emptyList());
        List<PaymentStatus> savedStatuses = new ArrayList<>();
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> {
            Refund saved = invocation.getArgument(0);
            savedStatuses.add(saved.getStatus());
            return saved;
        });

        refundService.processRefund(request);

        assertEquals(List.of(PaymentStatus.PROCESSING, PaymentStatus.COMPLETED), savedStatuses);
    }

    @Test
    void processRefundThrowsWhenPaymentIsMissing() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class, () -> refundService.processRefund(request));

        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
        verify(refundRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"PENDING", "PROCESSING", "FAILED", "REFUNDED"})
    void processRefundThrowsForNonCompletedPayments(PaymentStatus status) {
        completedPayment.setStatus(status);
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(completedPayment));

        PaymentException exception = assertThrows(PaymentException.class, () -> refundService.processRefund(request));

        assertEquals("INVALID_REFUND_STATE", exception.getErrorCode());
        verify(refundRepository, never()).save(any());
    }

    @Test
    void processRefundThrowsWhenAmountExceedsOriginalPayment() {
        request.setAmount(new BigDecimal("100.01"));
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(completedPayment));

        PaymentException exception = assertThrows(PaymentException.class, () -> refundService.processRefund(request));

        assertEquals("REFUND_EXCEEDS_PAYMENT", exception.getErrorCode());
    }

    @Test
    void processRefundAllowsFullAmountRefund() {
        request.setAmount(new BigDecimal("100.00"));
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(10L)).thenReturn(Collections.emptyList());
        stubRefundSave();

        Refund refund = refundService.processRefund(request);

        assertEquals(new BigDecimal("100.00"), refund.getAmount());
    }

    @Test
    void processRefundThrowsWhenPreviousRefundsWouldBeExceeded() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(10L)).thenReturn(List.of(
                Refund.builder().amount(new BigDecimal("70.00")).status(PaymentStatus.COMPLETED).build()));

        PaymentException exception = assertThrows(PaymentException.class, () -> refundService.processRefund(request));

        assertEquals("REFUND_TOTAL_EXCEEDED", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("70.00"));
    }

    @Test
    void processRefundIgnoresNonCompletedPreviousRefundsWhenTotalling() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(10L)).thenReturn(List.of(
                Refund.builder().amount(new BigDecimal("70.00")).status(PaymentStatus.FAILED).build(),
                Refund.builder().amount(new BigDecimal("30.00")).status(PaymentStatus.COMPLETED).build()));
        stubRefundSave();

        Refund refund = refundService.processRefund(request);

        assertEquals(PaymentStatus.COMPLETED, refund.getStatus());
    }

    @Test
    void processRefundSucceedsEvenWhenNotificationFails() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(10L)).thenReturn(Collections.emptyList());
        stubRefundSave();
        doThrow(new RuntimeException("webhook down"))
                .when(notificationService).sendRefundNotification(any(), any());

        Refund refund = refundService.processRefund(request);

        assertEquals(PaymentStatus.COMPLETED, refund.getStatus());
    }

    @Test
    void getRefundByIdReturnsStoredRefund() {
        Refund stored = Refund.builder().id(3L).refundId("RFD-ABC").build();
        when(refundRepository.findById(3L)).thenReturn(Optional.of(stored));

        assertSame(stored, refundService.getRefundById(3L));
    }

    @Test
    void getRefundByIdThrowsWhenMissing() {
        when(refundRepository.findById(3L)).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class, () -> refundService.getRefundById(3L));

        assertEquals("REFUND_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void getRefundsForPaymentDelegatesToRepository() {
        List<Refund> refunds = List.of(Refund.builder().id(1L).build());
        when(refundRepository.findByPaymentId(10L)).thenReturn(refunds);

        assertEquals(refunds, refundService.getRefundsForPayment(10L));
    }
}
