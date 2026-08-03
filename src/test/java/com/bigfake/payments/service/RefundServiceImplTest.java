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
import static org.mockito.Mockito.atLeastOnce;
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
                .id(7L)
                .transactionId("TXN-ORIGINAL01")
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();

        request = RefundRequest.builder()
                .paymentId(7L)
                .amount(new BigDecimal("40.00"))
                .reason("Customer returned the item")
                .initiatedBy("support-agent")
                .build();
    }

    private void stubRefundSaveEchoingArgument() {
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void processRefundCreatesACompletedRefundAndMarksThePaymentRefunded() {
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(7L)).thenReturn(Collections.emptyList());
        stubRefundSaveEchoingArgument();

        Refund refund = refundService.processRefund(request);

        assertEquals(PaymentStatus.COMPLETED, refund.getStatus());
        assertEquals(new BigDecimal("40.00"), refund.getAmount());
        assertEquals(7L, refund.getPaymentId());
        assertEquals("Customer returned the item", refund.getReason());
        assertEquals("support-agent", refund.getInitiatedBy());
        assertNotNull(refund.getProcessedAt());
        assertTrue(refund.getRefundId().startsWith("RFD-"));
        assertEquals(PaymentStatus.REFUNDED, completedPayment.getStatus());
        verify(paymentRepository).save(completedPayment);
        verify(notificationService).sendRefundNotification(refund, completedPayment);
    }

    @Test
    void processRefundGeneratesAUniqueUppercaseRefundId() {
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(7L)).thenReturn(Collections.emptyList());
        stubRefundSaveEchoingArgument();

        String first = refundService.processRefund(request).getRefundId();
        completedPayment.setStatus(PaymentStatus.COMPLETED);
        String second = refundService.processRefund(request).getRefundId();

        assertEquals(16, first.length());
        assertEquals(first, first.toUpperCase());
        assertTrue(!first.equals(second), "refund ids must be unique");
    }

    @Test
    void processRefundDefaultsInitiatedByToSystemWhenNotProvided() {
        request.setInitiatedBy(null);
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(7L)).thenReturn(Collections.emptyList());
        stubRefundSaveEchoingArgument();

        assertEquals("system", refundService.processRefund(request).getInitiatedBy());
    }

    @Test
    void processRefundAllowsARefundForTheFullPaymentAmount() {
        request.setAmount(new BigDecimal("100.00"));
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(7L)).thenReturn(Collections.emptyList());
        stubRefundSaveEchoingArgument();

        assertEquals(PaymentStatus.COMPLETED, refundService.processRefund(request).getStatus());
    }

    @Test
    void processRefundThrowsWhenThePaymentDoesNotExist() {
        when(paymentRepository.findById(7L)).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class, () -> refundService.processRefund(request));

        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
        verify(refundRepository, never()).save(any(Refund.class));
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"PENDING", "PROCESSING", "FAILED", "REFUNDED"})
    void processRefundRejectsPaymentsThatAreNotCompleted(PaymentStatus status) {
        completedPayment.setStatus(status);
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(completedPayment));

        PaymentException exception = assertThrows(PaymentException.class, () -> refundService.processRefund(request));

        assertEquals("INVALID_REFUND_STATE", exception.getErrorCode());
        verify(refundRepository, never()).save(any(Refund.class));
    }

    @Test
    void processRefundRejectsARefundLargerThanTheOriginalPayment() {
        request.setAmount(new BigDecimal("100.01"));
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(completedPayment));

        PaymentException exception = assertThrows(PaymentException.class, () -> refundService.processRefund(request));

        assertEquals("REFUND_EXCEEDS_PAYMENT", exception.getErrorCode());
        verify(refundRepository, never()).findByPaymentId(any());
    }

    @Test
    void processRefundRejectsARefundThatWouldExceedTheAlreadyRefundedTotal() {
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(7L)).thenReturn(List.of(
                completedRefund(new BigDecimal("70.00"))));

        PaymentException exception = assertThrows(PaymentException.class, () -> refundService.processRefund(request));

        assertEquals("REFUND_TOTAL_EXCEEDED", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("70.00"));
        verify(refundRepository, never()).save(any(Refund.class));
    }

    @Test
    void processRefundIgnoresNonCompletedRefundsWhenTotallingPreviousRefunds() {
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(7L)).thenReturn(Arrays.asList(
                refundWithStatus(new BigDecimal("70.00"), PaymentStatus.FAILED),
                refundWithStatus(new BigDecimal("70.00"), PaymentStatus.PROCESSING)));
        stubRefundSaveEchoingArgument();

        assertEquals(PaymentStatus.COMPLETED, refundService.processRefund(request).getStatus());
    }

    @Test
    void processRefundAllowsTopUpRefundsUpToTheOriginalAmount() {
        request.setAmount(new BigDecimal("60.00"));
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(7L)).thenReturn(List.of(
                completedRefund(new BigDecimal("40.00"))));
        stubRefundSaveEchoingArgument();

        assertEquals(PaymentStatus.COMPLETED, refundService.processRefund(request).getStatus());
    }

    @Test
    void processRefundPersistsTheRefundBeforeAndAfterGatewayProcessing() {
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(7L)).thenReturn(Collections.emptyList());
        stubRefundSaveEchoingArgument();

        refundService.processRefund(request);

        ArgumentCaptor<Refund> captor = ArgumentCaptor.forClass(Refund.class);
        verify(refundRepository, atLeastOnce()).save(captor.capture());
        assertEquals(2, captor.getAllValues().size());
        assertEquals(PaymentStatus.COMPLETED, captor.getValue().getStatus());
    }

    @Test
    void processRefundStillReturnsTheRefundWhenTheNotificationFails() {
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(7L)).thenReturn(Collections.emptyList());
        stubRefundSaveEchoingArgument();
        doThrow(new RuntimeException("webhook down"))
                .when(notificationService).sendRefundNotification(any(Refund.class), any(Payment.class));

        Refund refund = refundService.processRefund(request);

        assertEquals(PaymentStatus.COMPLETED, refund.getStatus());
    }

    @Test
    void getRefundByIdReturnsTheStoredRefund() {
        Refund stored = completedRefund(new BigDecimal("40.00"));
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
    void getRefundsForPaymentDelegatesToTheRepository() {
        List<Refund> refunds = List.of(completedRefund(new BigDecimal("10.00")));
        when(refundRepository.findByPaymentId(7L)).thenReturn(refunds);

        assertEquals(refunds, refundService.getRefundsForPayment(7L));
    }

    @Test
    void getRefundsForPaymentReturnsAnEmptyListWhenThereAreNone() {
        when(refundRepository.findByPaymentId(7L)).thenReturn(Collections.emptyList());

        assertEquals(List.of(), refundService.getRefundsForPayment(7L));
    }

    private static Refund completedRefund(BigDecimal amount) {
        return refundWithStatus(amount, PaymentStatus.COMPLETED);
    }

    private static Refund refundWithStatus(BigDecimal amount, PaymentStatus status) {
        return Refund.builder()
                .id(1L)
                .refundId("RFD-EXISTING01")
                .paymentId(7L)
                .amount(amount)
                .status(status)
                .build();
    }
}
