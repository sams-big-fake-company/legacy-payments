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
                .transactionId("TXN-COMPLETED001")
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();
    }

    // --- processRefund ---

    @Test
    void processRefund_fullRefund_success() {
        RefundRequest request = RefundRequest.builder()
                .paymentId(1L)
                .amount(new BigDecimal("100.00"))
                .reason("Customer requested")
                .initiatedBy("admin")
                .build();

        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(1L)).thenReturn(Collections.emptyList());
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> {
            Refund r = invocation.getArgument(0);
            r.setId(1L);
            return r;
        });
        when(paymentRepository.save(any(Payment.class))).thenReturn(completedPayment);

        Refund result = refundService.processRefund(request);

        assertNotNull(result);
        assertTrue(result.getRefundId().startsWith("RFD-"));
        assertEquals(new BigDecimal("100.00"), result.getAmount());
        assertEquals("admin", result.getInitiatedBy());
        verify(refundRepository, atLeast(2)).save(any(Refund.class));
        verify(notificationService).sendRefundNotification(any(Refund.class), any(Payment.class));
    }

    @Test
    void processRefund_partialRefund_success() {
        RefundRequest request = RefundRequest.builder()
                .paymentId(1L)
                .amount(new BigDecimal("50.00"))
                .reason("Partial return")
                .build();

        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(1L)).thenReturn(Collections.emptyList());
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> {
            Refund r = invocation.getArgument(0);
            r.setId(1L);
            return r;
        });
        when(paymentRepository.save(any(Payment.class))).thenReturn(completedPayment);

        Refund result = refundService.processRefund(request);

        assertNotNull(result);
        assertEquals(new BigDecimal("50.00"), result.getAmount());
        assertEquals("system", result.getInitiatedBy());
    }

    @Test
    void processRefund_paymentNotFound_throwsException() {
        RefundRequest request = RefundRequest.builder()
                .paymentId(999L)
                .amount(new BigDecimal("10.00"))
                .build();

        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        PaymentException ex = assertThrows(PaymentException.class,
                () -> refundService.processRefund(request));
        assertEquals("PAYMENT_NOT_FOUND", ex.getErrorCode());
    }

    @Test
    void processRefund_paymentNotCompleted_throwsException() {
        completedPayment.setStatus(PaymentStatus.PENDING);
        RefundRequest request = RefundRequest.builder()
                .paymentId(1L)
                .amount(new BigDecimal("10.00"))
                .build();

        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> refundService.processRefund(request));
        assertEquals("INVALID_REFUND_STATE", ex.getErrorCode());
    }

    @Test
    void processRefund_amountExceedsPayment_throwsException() {
        RefundRequest request = RefundRequest.builder()
                .paymentId(1L)
                .amount(new BigDecimal("150.00"))
                .build();

        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> refundService.processRefund(request));
        assertEquals("REFUND_EXCEEDS_PAYMENT", ex.getErrorCode());
    }

    @Test
    void processRefund_totalRefundsExceedPayment_throwsException() {
        Refund existingRefund = Refund.builder()
                .id(1L)
                .paymentId(1L)
                .amount(new BigDecimal("80.00"))
                .status(PaymentStatus.COMPLETED)
                .build();

        RefundRequest request = RefundRequest.builder()
                .paymentId(1L)
                .amount(new BigDecimal("30.00"))
                .build();

        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(1L)).thenReturn(List.of(existingRefund));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> refundService.processRefund(request));
        assertEquals("REFUND_TOTAL_EXCEEDED", ex.getErrorCode());
    }

    @Test
    void processRefund_pendingRefundsIgnoredInTotal() {
        Refund pendingRefund = Refund.builder()
                .id(1L)
                .paymentId(1L)
                .amount(new BigDecimal("80.00"))
                .status(PaymentStatus.PENDING)
                .build();

        RefundRequest request = RefundRequest.builder()
                .paymentId(1L)
                .amount(new BigDecimal("100.00"))
                .build();

        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(1L)).thenReturn(List.of(pendingRefund));
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> {
            Refund r = invocation.getArgument(0);
            r.setId(2L);
            return r;
        });
        when(paymentRepository.save(any(Payment.class))).thenReturn(completedPayment);

        Refund result = refundService.processRefund(request);
        assertNotNull(result);
    }

    @Test
    void processRefund_notificationFailure_doesNotThrow() {
        RefundRequest request = RefundRequest.builder()
                .paymentId(1L)
                .amount(new BigDecimal("50.00"))
                .build();

        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(1L)).thenReturn(Collections.emptyList());
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> {
            Refund r = invocation.getArgument(0);
            r.setId(1L);
            return r;
        });
        when(paymentRepository.save(any(Payment.class))).thenReturn(completedPayment);
        doThrow(new RuntimeException("Notification failed"))
                .when(notificationService).sendRefundNotification(any(), any());

        assertDoesNotThrow(() -> refundService.processRefund(request));
    }

    // --- getRefundById ---

    @Test
    void getRefundById_found_returnsRefund() {
        Refund refund = Refund.builder()
                .id(1L)
                .refundId("RFD-TEST123")
                .amount(new BigDecimal("50.00"))
                .build();
        when(refundRepository.findById(1L)).thenReturn(Optional.of(refund));

        Refund result = refundService.getRefundById(1L);
        assertEquals("RFD-TEST123", result.getRefundId());
    }

    @Test
    void getRefundById_notFound_throwsException() {
        when(refundRepository.findById(999L)).thenReturn(Optional.empty());

        PaymentException ex = assertThrows(PaymentException.class,
                () -> refundService.getRefundById(999L));
        assertEquals("REFUND_NOT_FOUND", ex.getErrorCode());
    }

    // --- getRefundsForPayment ---

    @Test
    void getRefundsForPayment_returnsRefundList() {
        List<Refund> refunds = List.of(
                Refund.builder().id(1L).refundId("RFD-1").build(),
                Refund.builder().id(2L).refundId("RFD-2").build()
        );
        when(refundRepository.findByPaymentId(1L)).thenReturn(refunds);

        List<Refund> result = refundService.getRefundsForPayment(1L);
        assertEquals(2, result.size());
    }

    @Test
    void getRefundsForPayment_noRefunds_returnsEmptyList() {
        when(refundRepository.findByPaymentId(1L)).thenReturn(Collections.emptyList());

        List<Refund> result = refundService.getRefundsForPayment(1L);
        assertTrue(result.isEmpty());
    }
}
