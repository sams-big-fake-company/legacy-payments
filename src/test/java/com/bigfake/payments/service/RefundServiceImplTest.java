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

    @BeforeEach
    void setUp() {
        completedPayment = Payment.builder()
                .id(1L)
                .transactionId("TXN-ORIGINAL1")
                .merchantId(1L)
                .amount(new BigDecimal("200.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();
    }

    private static RefundRequest.RefundRequestBuilder request() {
        return RefundRequest.builder()
                .paymentId(1L)
                .amount(new BigDecimal("50.00"))
                .reason("Customer changed their mind");
    }

    private void paymentFound() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));
    }

    private void saveEchoesRefund() {
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Refund lastSavedRefund() {
        ArgumentCaptor<Refund> captor = ArgumentCaptor.forClass(Refund.class);
        verify(refundRepository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void processRefund_paymentNotFound() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class,
                () -> refundService.processRefund(request().build()));

        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
        verify(refundRepository, never()).save(any(Refund.class));
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"PENDING", "PROCESSING", "FAILED", "REFUNDED"})
    void processRefund_onlyCompletedPaymentsAreRefundable(PaymentStatus status) {
        completedPayment.setStatus(status);
        paymentFound();

        PaymentException exception = assertThrows(PaymentException.class,
                () -> refundService.processRefund(request().build()));

        assertEquals("INVALID_REFUND_STATE", exception.getErrorCode());
        verify(refundRepository, never()).save(any(Refund.class));
    }

    @Test
    void processRefund_amountAboveTheOriginalPaymentIsRejected() {
        paymentFound();

        PaymentException exception = assertThrows(PaymentException.class,
                () -> refundService.processRefund(request().amount(new BigDecimal("200.01")).build()));

        assertEquals("REFUND_EXCEEDS_PAYMENT", exception.getErrorCode());
        verify(refundRepository, never()).findByPaymentId(anyLong());
    }

    @Test
    void processRefund_completedRefundsCountTowardsTheRefundableTotal() {
        paymentFound();
        when(refundRepository.findByPaymentId(1L)).thenReturn(Arrays.asList(
                Refund.builder().amount(new BigDecimal("150.00")).status(PaymentStatus.COMPLETED).build(),
                Refund.builder().amount(new BigDecimal("20.00")).status(PaymentStatus.COMPLETED).build()));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> refundService.processRefund(request().amount(new BigDecimal("40.00")).build()));

        assertEquals("REFUND_TOTAL_EXCEEDED", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Already refunded: 170.00"), exception.getMessage());
    }

    @Test
    void processRefund_failedRefundsDoNotCountTowardsTheRefundableTotal() {
        paymentFound();
        when(refundRepository.findByPaymentId(1L)).thenReturn(Collections.singletonList(
                Refund.builder().amount(new BigDecimal("200.00")).status(PaymentStatus.FAILED).build()));
        saveEchoesRefund();

        Refund refund = refundService.processRefund(request().amount(new BigDecimal("200.00")).build());

        assertEquals(PaymentStatus.COMPLETED, refund.getStatus());
    }

    @Test
    void processRefund_fullRefundMarksTheOriginalPaymentRefunded() {
        paymentFound();
        when(refundRepository.findByPaymentId(1L)).thenReturn(Collections.emptyList());
        saveEchoesRefund();

        Refund refund = refundService.processRefund(request().amount(new BigDecimal("200.00")).build());

        assertEquals(PaymentStatus.COMPLETED, refund.getStatus());
        assertNotNull(refund.getProcessedAt());
        assertEquals(1L, refund.getPaymentId());
        assertTrue(refund.getRefundId().startsWith("RFD-"), refund.getRefundId());
        verify(paymentRepository).save(argThat(p -> p.getStatus() == PaymentStatus.REFUNDED));
    }

    @Test
    void processRefund_partialRefundRecordsReasonAndInitiator() {
        paymentFound();
        when(refundRepository.findByPaymentId(1L)).thenReturn(Collections.emptyList());
        saveEchoesRefund();

        refundService.processRefund(request().initiatedBy("support-agent-7").build());

        Refund saved = lastSavedRefund();
        assertEquals(new BigDecimal("50.00"), saved.getAmount());
        assertEquals("Customer changed their mind", saved.getReason());
        assertEquals("support-agent-7", saved.getInitiatedBy());
    }

    @Test
    void processRefund_defaultsInitiatedByToSystem() {
        paymentFound();
        when(refundRepository.findByPaymentId(1L)).thenReturn(Collections.emptyList());
        saveEchoesRefund();

        refundService.processRefund(request().initiatedBy(null).build());

        assertEquals("system", lastSavedRefund().getInitiatedBy());
    }

    @Test
    void processRefund_notifiesTheMerchantWithTheOriginalPayment() {
        paymentFound();
        when(refundRepository.findByPaymentId(1L)).thenReturn(Collections.emptyList());
        saveEchoesRefund();

        Refund refund = refundService.processRefund(request().build());

        verify(notificationService).sendRefundNotification(refund, completedPayment);
    }

    @Test
    void processRefund_notificationFailureDoesNotFailTheRefund() {
        paymentFound();
        when(refundRepository.findByPaymentId(1L)).thenReturn(Collections.emptyList());
        saveEchoesRefund();
        doThrow(new IllegalStateException("webhook down"))
                .when(notificationService).sendRefundNotification(any(Refund.class), any(Payment.class));

        Refund refund = refundService.processRefund(request().build());

        assertEquals(PaymentStatus.COMPLETED, refund.getStatus());
    }

    @Test
    void getRefundById_returnsTheRefund() {
        Refund refund = Refund.builder().id(5L).refundId("RFD-ABC").build();
        when(refundRepository.findById(5L)).thenReturn(Optional.of(refund));

        assertSame(refund, refundService.getRefundById(5L));
    }

    @Test
    void getRefundById_notFound() {
        when(refundRepository.findById(404L)).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class, () -> refundService.getRefundById(404L));

        assertEquals("REFUND_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void getRefundsForPayment_delegatesToTheRepository() {
        List<Refund> refunds = Collections.singletonList(Refund.builder().id(1L).build());
        when(refundRepository.findByPaymentId(1L)).thenReturn(refunds);

        assertEquals(refunds, refundService.getRefundsForPayment(1L));
    }
}
