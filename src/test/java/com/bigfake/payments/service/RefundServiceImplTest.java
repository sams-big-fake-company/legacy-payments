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
                .transactionId("TXN-ORIGINAL")
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
                .initiatedBy("agent-42")
                .build();
    }

    private void stubSaveEchoingRefund() {
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void processRefund_partialRefund_completesAndMarksPaymentRefunded() {
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(7L)).thenReturn(Collections.emptyList());
        stubSaveEchoingRefund();

        Refund refund = refundService.processRefund(request);

        assertEquals(PaymentStatus.COMPLETED, refund.getStatus());
        assertEquals(new BigDecimal("40.00"), refund.getAmount());
        assertEquals(7L, refund.getPaymentId());
        assertEquals("Customer returned item", refund.getReason());
        assertEquals("agent-42", refund.getInitiatedBy());
        assertTrue(refund.getRefundId().startsWith("RFD-"));
        assertNotNull(refund.getProcessedAt());
        assertEquals(PaymentStatus.REFUNDED, completedPayment.getStatus());
        verify(paymentRepository).save(completedPayment);
        verify(notificationService).sendRefundNotification(refund, completedPayment);
    }

    @Test
    void processRefund_fullRefund_isAllowed() {
        request.setAmount(new BigDecimal("100.00"));
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(7L)).thenReturn(Collections.emptyList());
        stubSaveEchoingRefund();

        Refund refund = refundService.processRefund(request);

        assertEquals(new BigDecimal("100.00"), refund.getAmount());
        assertEquals(PaymentStatus.COMPLETED, refund.getStatus());
    }

    @Test
    void processRefund_missingInitiatedBy_defaultsToSystem() {
        request.setInitiatedBy(null);
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(7L)).thenReturn(Collections.emptyList());
        stubSaveEchoingRefund();

        assertEquals("system", refundService.processRefund(request).getInitiatedBy());
    }

    @Test
    void processRefund_paymentNotFound_throwsPaymentNotFound() {
        when(paymentRepository.findById(7L)).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class, () -> refundService.processRefund(request));

        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
        verify(refundRepository, never()).save(any(Refund.class));
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"PENDING", "PROCESSING", "FAILED", "REFUNDED"})
    void processRefund_nonCompletedPayment_throwsInvalidRefundState(PaymentStatus status) {
        completedPayment.setStatus(status);
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(completedPayment));

        PaymentException exception = assertThrows(PaymentException.class, () -> refundService.processRefund(request));

        assertEquals("INVALID_REFUND_STATE", exception.getErrorCode());
        verify(refundRepository, never()).save(any(Refund.class));
    }

    @Test
    void processRefund_amountAboveOriginalPayment_throwsRefundExceedsPayment() {
        request.setAmount(new BigDecimal("100.01"));
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(completedPayment));

        PaymentException exception = assertThrows(PaymentException.class, () -> refundService.processRefund(request));

        assertEquals("REFUND_EXCEEDS_PAYMENT", exception.getErrorCode());
    }

    @Test
    void processRefund_totalOfCompletedRefundsExceedsPayment_throwsRefundTotalExceeded() {
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(7L)).thenReturn(Arrays.asList(
                refundWith(new BigDecimal("50.00"), PaymentStatus.COMPLETED),
                refundWith(new BigDecimal("30.00"), PaymentStatus.COMPLETED)));

        PaymentException exception = assertThrows(PaymentException.class, () -> refundService.processRefund(request));

        assertEquals("REFUND_TOTAL_EXCEEDED", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("80.00"));
    }

    @Test
    void processRefund_ignoresNonCompletedExistingRefundsInTotal() {
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(7L)).thenReturn(Arrays.asList(
                refundWith(new BigDecimal("70.00"), PaymentStatus.FAILED),
                refundWith(new BigDecimal("60.00"), PaymentStatus.PROCESSING)));
        stubSaveEchoingRefund();

        assertEquals(PaymentStatus.COMPLETED, refundService.processRefund(request).getStatus());
    }

    @Test
    void processRefund_existingRefundsUpToRemainingBalance_isAllowed() {
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(7L))
                .thenReturn(Collections.singletonList(refundWith(new BigDecimal("60.00"), PaymentStatus.COMPLETED)));
        stubSaveEchoingRefund();

        assertEquals(PaymentStatus.COMPLETED, refundService.processRefund(request).getStatus());
    }

    @Test
    void processRefund_savesRefundBeforeAndAfterGatewayCall() {
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(7L)).thenReturn(Collections.emptyList());
        stubSaveEchoingRefund();

        refundService.processRefund(request);

        ArgumentCaptor<Refund> captor = ArgumentCaptor.forClass(Refund.class);
        verify(refundRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertEquals(2, captor.getAllValues().size());
    }

    @Test
    void processRefund_notificationFailure_doesNotFailRefund() {
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(7L)).thenReturn(Collections.emptyList());
        stubSaveEchoingRefund();
        doThrow(new RuntimeException("webhook down"))
                .when(notificationService).sendRefundNotification(any(Refund.class), any(Payment.class));

        assertEquals(PaymentStatus.COMPLETED, refundService.processRefund(request).getStatus());
    }

    @Test
    void getRefundById_existingRefund_isReturned() {
        Refund refund = refundWith(new BigDecimal("10.00"), PaymentStatus.COMPLETED);
        when(refundRepository.findById(3L)).thenReturn(Optional.of(refund));

        assertSame(refund, refundService.getRefundById(3L));
    }

    @Test
    void getRefundById_missingRefund_throwsRefundNotFound() {
        when(refundRepository.findById(3L)).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class, () -> refundService.getRefundById(3L));

        assertEquals("REFUND_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void getRefundsForPayment_delegatesToRepository() {
        List<Refund> refunds = Collections.singletonList(refundWith(new BigDecimal("5.00"), PaymentStatus.COMPLETED));
        when(refundRepository.findByPaymentId(7L)).thenReturn(refunds);

        assertEquals(refunds, refundService.getRefundsForPayment(7L));
    }

    private static Refund refundWith(BigDecimal amount, PaymentStatus status) {
        return Refund.builder()
                .id(1L)
                .refundId("RFD-EXISTING")
                .paymentId(7L)
                .amount(amount)
                .status(status)
                .build();
    }
}
