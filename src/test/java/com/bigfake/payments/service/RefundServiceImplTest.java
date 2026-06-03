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
    private RefundRequest validRefundRequest;

    @BeforeEach
    void setUp() {
        completedPayment = Payment.builder()
                .id(1L)
                .transactionId("TXN-ABC123")
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();

        validRefundRequest = RefundRequest.builder()
                .paymentId(1L)
                .amount(new BigDecimal("50.00"))
                .reason("Customer request")
                .initiatedBy("admin")
                .build();
    }

    @Test
    void processRefund_success() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(1L)).thenReturn(Collections.emptyList());
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> {
            Refund r = invocation.getArgument(0);
            r.setId(1L);
            return r;
        });
        when(paymentRepository.save(any(Payment.class))).thenReturn(completedPayment);

        Refund refund = refundService.processRefund(validRefundRequest);

        assertNotNull(refund);
        assertNotNull(refund.getRefundId());
        assertTrue(refund.getRefundId().startsWith("RFD-"));
        assertEquals(new BigDecimal("50.00"), refund.getAmount());
        verify(refundRepository, atLeast(2)).save(any(Refund.class));
        verify(notificationService).sendRefundNotification(any(Refund.class), any(Payment.class));
    }

    @Test
    void processRefund_paymentNotFound() {
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());
        validRefundRequest.setPaymentId(999L);

        PaymentException ex = assertThrows(PaymentException.class,
                () -> refundService.processRefund(validRefundRequest));
        assertEquals("PAYMENT_NOT_FOUND", ex.getErrorCode());
    }

    @Test
    void processRefund_paymentNotCompleted() {
        completedPayment.setStatus(PaymentStatus.PENDING);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> refundService.processRefund(validRefundRequest));
        assertEquals("INVALID_REFUND_STATE", ex.getErrorCode());
    }

    @Test
    void processRefund_amountExceedsPayment() {
        validRefundRequest.setAmount(new BigDecimal("150.00"));
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> refundService.processRefund(validRefundRequest));
        assertEquals("REFUND_EXCEEDS_PAYMENT", ex.getErrorCode());
    }

    @Test
    void processRefund_totalRefundsWouldExceedPayment() {
        Refund existingRefund = Refund.builder()
                .id(1L)
                .amount(new BigDecimal("80.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(1L)).thenReturn(List.of(existingRefund));

        validRefundRequest.setAmount(new BigDecimal("30.00"));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> refundService.processRefund(validRefundRequest));
        assertEquals("REFUND_TOTAL_EXCEEDED", ex.getErrorCode());
    }

    @Test
    void processRefund_existingFailedRefundsNotCounted() {
        Refund failedRefund = Refund.builder()
                .id(1L)
                .amount(new BigDecimal("80.00"))
                .status(PaymentStatus.FAILED)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(1L)).thenReturn(List.of(failedRefund));
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> {
            Refund r = invocation.getArgument(0);
            r.setId(2L);
            return r;
        });
        when(paymentRepository.save(any(Payment.class))).thenReturn(completedPayment);

        Refund refund = refundService.processRefund(validRefundRequest);
        assertNotNull(refund);
    }

    @Test
    void processRefund_nullInitiatedBy_defaultsToSystem() {
        validRefundRequest.setInitiatedBy(null);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(1L)).thenReturn(Collections.emptyList());
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> {
            Refund r = invocation.getArgument(0);
            r.setId(1L);
            return r;
        });
        when(paymentRepository.save(any(Payment.class))).thenReturn(completedPayment);

        Refund refund = refundService.processRefund(validRefundRequest);
        assertEquals("system", refund.getInitiatedBy());
    }

    @Test
    void processRefund_notificationFailure_doesNotFailRefund() {
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

        Refund refund = refundService.processRefund(validRefundRequest);
        assertNotNull(refund);
    }

    @Test
    void getRefundById_found() {
        Refund refund = Refund.builder().id(1L).refundId("RFD-123").build();
        when(refundRepository.findById(1L)).thenReturn(Optional.of(refund));

        Refund result = refundService.getRefundById(1L);
        assertEquals("RFD-123", result.getRefundId());
    }

    @Test
    void getRefundById_notFound() {
        when(refundRepository.findById(999L)).thenReturn(Optional.empty());

        PaymentException ex = assertThrows(PaymentException.class,
                () -> refundService.getRefundById(999L));
        assertEquals("REFUND_NOT_FOUND", ex.getErrorCode());
    }

    @Test
    void getRefundsForPayment_returnsList() {
        List<Refund> refunds = List.of(
                Refund.builder().id(1L).paymentId(1L).build(),
                Refund.builder().id(2L).paymentId(1L).build()
        );
        when(refundRepository.findByPaymentId(1L)).thenReturn(refunds);

        List<Refund> result = refundService.getRefundsForPayment(1L);
        assertEquals(2, result.size());
    }

    @Test
    void getRefundsForPayment_emptyList() {
        when(refundRepository.findByPaymentId(999L)).thenReturn(Collections.emptyList());

        List<Refund> result = refundService.getRefundsForPayment(999L);
        assertTrue(result.isEmpty());
    }
}
