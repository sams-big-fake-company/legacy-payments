package com.bigfake.payments.service;

import com.bigfake.payments.exception.InsufficientFundsException;
import com.bigfake.payments.exception.PaymentException;
import com.bigfake.payments.model.dto.PaymentRequest;
import com.bigfake.payments.model.dto.PaymentResponse;
import com.bigfake.payments.model.entity.Merchant;
import com.bigfake.payments.model.entity.Payment;
import com.bigfake.payments.model.enums.PaymentStatus;
import com.bigfake.payments.model.enums.PaymentType;
import com.bigfake.payments.repository.MerchantRepository;
import com.bigfake.payments.repository.PaymentRepository;
import com.bigfake.payments.service.impl.PaymentServiceImpl;
import com.bigfake.payments.util.CurrencyConverter;
import com.bigfake.payments.util.PaymentValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Edge case and branch coverage tests for PaymentServiceImpl,
 * complementing the happy-path tests in PaymentServiceImplTest.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplEdgeCaseTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private PaymentValidator paymentValidator;

    @Mock
    private CurrencyConverter currencyConverter;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchant = Merchant.builder()
                .id(1L)
                .merchantCode("MERCH001")
                .businessName("Test Store")
                .isActive(true)
                .dailyLimit(new BigDecimal("100000.00"))
                .build();
    }

    private PaymentRequest.PaymentRequestBuilder baseRequest() {
        return PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("500.00"))
                .currency("USD");
    }

    private void stubSave() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });
    }

    @Test
    void processPayment_unsupportedCurrency() {
        PaymentRequest request = baseRequest()
                .currency("XYZ")
                .paymentType(PaymentType.WIRE)
                .customerName("Alice")
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        when(currencyConverter.convertToUsd(any(), eq("XYZ"))).thenReturn(null);

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("UNSUPPORTED_CURRENCY", ex.getErrorCode());
    }

    @Test
    void processPayment_foreignCurrencyIsConverted() {
        PaymentRequest request = baseRequest()
                .currency("EUR")
                .paymentType(PaymentType.WIRE)
                .customerName("Alice")
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        when(currencyConverter.convertToUsd(new BigDecimal("500.00"), "EUR"))
                .thenReturn(new BigDecimal("543.75"));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(BigDecimal.ZERO);
        stubSave();

        PaymentResponse response = paymentService.processPayment(request);

        assertEquals("EUR", response.getCurrency());
        verify(currencyConverter).convertToUsd(new BigDecimal("500.00"), "EUR");
    }

    @Test
    void processPayment_wireBelowMinimum() {
        PaymentRequest request = baseRequest()
                .amount(new BigDecimal("50.00"))
                .paymentType(PaymentType.WIRE)
                .customerName("Alice")
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("WIRE_MINIMUM_NOT_MET", ex.getErrorCode());
    }

    @Test
    void processPayment_wireRequiresCustomerName() {
        PaymentRequest request = baseRequest()
                .paymentType(PaymentType.WIRE)
                .customerName("   ")
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("WIRE_NAME_REQUIRED", ex.getErrorCode());
    }

    @Test
    void processPayment_achDailyLimitExceeded() {
        PaymentRequest request = baseRequest()
                .amount(new BigDecimal("1000.00"))
                .paymentType(PaymentType.ACH)
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(new BigDecimal("24500.00"));

        InsufficientFundsException ex = assertThrows(InsufficientFundsException.class,
                () -> paymentService.processPayment(request));
        assertEquals(new BigDecimal("1000.00"), ex.getRequestedAmount());
        assertEquals(new BigDecimal("500.00"), ex.getAvailableAmount());
    }

    @Test
    void processPayment_debitRequiresCardLastFour() {
        PaymentRequest request = baseRequest()
                .paymentType(PaymentType.DEBIT)
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("CARD_INFO_REQUIRED", ex.getErrorCode());
    }

    @Test
    void processPayment_creditCardRequiresCardLastFour() {
        PaymentRequest request = baseRequest()
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("42")
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("CARD_INFO_REQUIRED", ex.getErrorCode());
    }

    @Test
    void processPayment_velocityExceeded() {
        PaymentRequest request = baseRequest()
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        List<Payment> recentPayments = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            recentPayments.add(Payment.builder().id((long) i).build());
        }
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(recentPayments);

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("VELOCITY_EXCEEDED", ex.getErrorCode());
    }

    @Test
    void processPayment_merchantDailyLimitExceeded() {
        merchant.setDailyLimit(new BigDecimal("1000.00"));
        PaymentRequest request = baseRequest()
                .amount(new BigDecimal("600.00"))
                .paymentType(PaymentType.WIRE)
                .customerName("Alice")
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(new BigDecimal("700.00"));

        InsufficientFundsException ex = assertThrows(InsufficientFundsException.class,
                () -> paymentService.processPayment(request));
        assertEquals(new BigDecimal("300.00"), ex.getAvailableAmount());
    }

    @Test
    void processPayment_nullDailyLimitSkipsLimitCheck() {
        merchant.setDailyLimit(null);
        PaymentRequest request = baseRequest()
                .paymentType(PaymentType.WIRE)
                .customerName("Alice")
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        stubSave();

        PaymentResponse response = paymentService.processPayment(request);

        assertNotNull(response.getTransactionId());
        verify(paymentRepository, never()).sumCompletedAmountByMerchantSince(anyLong(), any());
    }

    @Test
    void processPayment_calculatesFeesAndNetAmount() {
        PaymentRequest request = baseRequest()
                .amount(new BigDecimal("100.00"))
                .paymentType(PaymentType.ACH)
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(BigDecimal.ZERO);
        stubSave();

        PaymentResponse response = paymentService.processPayment(request);

        assertEquals(new BigDecimal("0.8000"), response.getFeeAmount());
        assertEquals(new BigDecimal("99.2000"), response.getNetAmount());
    }

    @Test
    void processPayment_notificationFailureDoesNotFailPayment() {
        PaymentRequest request = baseRequest()
                .paymentType(PaymentType.WIRE)
                .customerName("Alice")
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(BigDecimal.ZERO);
        stubSave();
        doThrow(new RuntimeException("smtp down"))
                .when(notificationService).sendPaymentNotification(any(Payment.class));

        PaymentResponse response = paymentService.processPayment(request);

        assertNotNull(response.getTransactionId());
    }

    @Test
    void getPaymentByTransactionId_found() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-FOUND")
                .merchantId(1L)
                .amount(BigDecimal.TEN)
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.ACH)
                .build();
        when(paymentRepository.findByTransactionId("TXN-FOUND")).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentByTransactionId("TXN-FOUND");

        assertEquals("TXN-FOUND", response.getTransactionId());
    }

    @Test
    void getPaymentByTransactionId_notFound() {
        when(paymentRepository.findByTransactionId("TXN-MISSING")).thenReturn(Optional.empty());

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.getPaymentByTransactionId("TXN-MISSING"));
        assertEquals("PAYMENT_NOT_FOUND", ex.getErrorCode());
    }

    @Test
    void getPaymentsByMerchant_mapsAllPayments() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-LIST")
                .merchantId(1L)
                .amount(BigDecimal.TEN)
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.ACH)
                .build();
        when(paymentRepository.findByMerchantIdAndStatus(1L, null)).thenReturn(List.of(payment));

        List<PaymentResponse> responses = paymentService.getPaymentsByMerchant(1L);

        assertEquals(1, responses.size());
        assertEquals("TXN-LIST", responses.get(0).getTransactionId());
    }

    @Test
    void getPaymentsByMerchant_emptyList() {
        when(paymentRepository.findByMerchantIdAndStatus(1L, null)).thenReturn(Collections.emptyList());

        assertEquals(Collections.emptyList(), paymentService.getPaymentsByMerchant(1L));
    }

    @Test
    void updatePaymentStatus_allowsNonTerminalTransition() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-UPDATE")
                .merchantId(1L)
                .amount(BigDecimal.TEN)
                .currency("USD")
                .status(PaymentStatus.PROCESSING)
                .paymentType(PaymentType.ACH)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED);

        assertEquals(PaymentStatus.COMPLETED, response.getStatus());
        assertNotNull(response.getCompletedAt());
    }

    @Test
    void updatePaymentStatus_rejectsTransitionFromTerminalState() {
        Payment payment = Payment.builder()
                .id(1L)
                .status(PaymentStatus.FAILED)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED));
        assertEquals("INVALID_STATE_TRANSITION", ex.getErrorCode());
    }

    @Test
    void updatePaymentStatus_allowsCompletedToRefunded() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-REFUND")
                .merchantId(1L)
                .amount(BigDecimal.TEN)
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.ACH)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = paymentService.updatePaymentStatus(1L, PaymentStatus.REFUNDED);

        assertEquals(PaymentStatus.REFUNDED, response.getStatus());
    }

    @Test
    void updatePaymentStatus_paymentNotFound() {
        when(paymentRepository.findById(99L)).thenReturn(Optional.empty());

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.updatePaymentStatus(99L, PaymentStatus.COMPLETED));
        assertEquals("PAYMENT_NOT_FOUND", ex.getErrorCode());
    }

    @Test
    void cancelPayment_paymentNotFound() {
        when(paymentRepository.findById(99L)).thenReturn(Optional.empty());

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.cancelPayment(99L));
        assertEquals("PAYMENT_NOT_FOUND", ex.getErrorCode());
    }
}
