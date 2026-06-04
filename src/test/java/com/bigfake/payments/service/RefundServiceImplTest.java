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
                .transactionId("TXN-REFUND001")
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .customerEmail("customer@example.com")
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

        Refund result = refundService.processRefund(validRefundRequest);

        assertNotNull(result);
        assertNotNull(result.getRefundId());
        assertTrue(result.getRefundId().startsWith("RFD-"));
        assertEquals(new BigDecimal("50.00"), result.getAmount());
        assertEquals("Customer request", result.getReason());
        assertEquals("admin", result.getInitiatedBy());
        verify(paymentRepository).save(any(Payment.class));
        verify(notificationService).sendRefundNotification(any(Refund.class), any(Payment.class));
    }

    @Test
    void processRefund_paymentNotFound() {
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());
        validRefundRequest.setPaymentId(999L);

        PaymentException exception = assertThrows(PaymentException.class,
                () -> refundService.processRefund(validRefundRequest));
        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void processRefund_paymentNotCompleted() {
        completedPayment.setStatus(PaymentStatus.PENDING);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> refundService.processRefund(validRefundRequest));
        assertEquals("INVALID_REFUND_STATE", exception.getErrorCode());
    }

    @Test
    void processRefund_amountExceedsPayment() {
        validRefundRequest.setAmount(new BigDecimal("200.00"));
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> refundService.processRefund(validRefundRequest));
        assertEquals("REFUND_EXCEEDS_PAYMENT", exception.getErrorCode());
    }

    @Test
    void processRefund_totalRefundExceedsPayment() {
        Refund existingRefund = Refund.builder()
                .id(1L)
                .paymentId(1L)
                .amount(new BigDecimal("80.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(1L)).thenReturn(List.of(existingRefund));

        validRefundRequest.setAmount(new BigDecimal("30.00"));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> refundService.processRefund(validRefundRequest));
        assertEquals("REFUND_TOTAL_EXCEEDED", exception.getErrorCode());
    }

    @Test
    void processRefund_defaultInitiatedBy() {
        validRefundRequest.setInitiatedBy(null);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(1L)).thenReturn(Collections.emptyList());
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> {
            Refund r = invocation.getArgument(0);
            r.setId(2L);
            return r;
        });

        Refund result = refundService.processRefund(validRefundRequest);

        assertEquals("system", result.getInitiatedBy());
    }

    @Test
    void processRefund_notificationFailureDoesNotAffectRefund() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));
        when(refundRepository.findByPaymentId(1L)).thenReturn(Collections.emptyList());
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> {
            Refund r = invocation.getArgument(0);
            r.setId(3L);
            return r;
        });
        doThrow(new RuntimeException("Email service down"))
                .when(notificationService).sendRefundNotification(any(Refund.class), any(Payment.class));

        Refund result = refundService.processRefund(validRefundRequest);

        assertNotNull(result);
    }

    @Test
    void getRefundById_success() {
        Refund refund = Refund.builder()
                .id(1L)
                .refundId("RFD-ABC123")
                .paymentId(1L)
                .amount(new BigDecimal("25.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        when(refundRepository.findById(1L)).thenReturn(Optional.of(refund));

        Refund result = refundService.getRefundById(1L);

        assertNotNull(result);
        assertEquals("RFD-ABC123", result.getRefundId());
    }

    @Test
    void getRefundById_notFound() {
        when(refundRepository.findById(999L)).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class,
                () -> refundService.getRefundById(999L));
        assertEquals("REFUND_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void getRefundsForPayment_returnsList() {
        Refund refund1 = Refund.builder().id(1L).paymentId(1L).amount(new BigDecimal("20.00")).build();
        Refund refund2 = Refund.builder().id(2L).paymentId(1L).amount(new BigDecimal("30.00")).build();
        when(refundRepository.findByPaymentId(1L)).thenReturn(List.of(refund1, refund2));

        List<Refund> results = refundService.getRefundsForPayment(1L);

        assertEquals(2, results.size());
    }

    @Test
    void getRefundsForPayment_emptyList() {
        when(refundRepository.findByPaymentId(1L)).thenReturn(Collections.emptyList());

        List<Refund> results = refundService.getRefundsForPayment(1L);

        assertTrue(results.isEmpty());
    }
}
