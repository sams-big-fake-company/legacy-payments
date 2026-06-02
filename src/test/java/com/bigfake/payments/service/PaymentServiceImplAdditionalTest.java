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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplAdditionalTest {

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

    private Merchant testMerchant;

    @BeforeEach
    void setUp() {
        testMerchant = Merchant.builder()
                .id(1L)
                .merchantCode("MERCH001")
                .businessName("Test Store")
                .isActive(true)
                .dailyLimit(new BigDecimal("100000.00"))
                .build();
    }

    // --- Wire transfer tests ---

    @Test
    void processPayment_wireTransfer_belowMinimum_throwsException() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentType(PaymentType.WIRE)
                .customerName("John Doe")
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("WIRE_MINIMUM_NOT_MET", exception.getErrorCode());
    }

    @Test
    void processPayment_wireTransfer_missingCustomerName_throwsException() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("200.00"))
                .currency("USD")
                .paymentType(PaymentType.WIRE)
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("WIRE_NAME_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_wireTransfer_emptyCustomerName_throwsException() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("200.00"))
                .currency("USD")
                .paymentType(PaymentType.WIRE)
                .customerName("   ")
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("WIRE_NAME_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_wireTransfer_success() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("200.00"))
                .currency("USD")
                .paymentType(PaymentType.WIRE)
                .customerName("John Doe")
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });

        PaymentResponse response = paymentService.processPayment(request);

        assertNotNull(response);
        assertEquals(PaymentType.WIRE, response.getPaymentType());
    }

    // --- ACH tests ---

    @Test
    void processPayment_ach_exceedsDailyLimit_throwsException() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("20000.00"))
                .currency("USD")
                .paymentType(PaymentType.ACH)
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(new BigDecimal("10000.00"));

        assertThrows(InsufficientFundsException.class,
                () -> paymentService.processPayment(request));
    }

    @Test
    void processPayment_ach_withinDailyLimit_success() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .paymentType(PaymentType.ACH)
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });

        PaymentResponse response = paymentService.processPayment(request);
        assertNotNull(response);
    }

    // --- Debit card tests ---

    @Test
    void processPayment_debit_missingCardLastFour_throwsException() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentType(PaymentType.DEBIT)
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("CARD_INFO_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_debit_invalidCardLastFour_throwsException() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentType(PaymentType.DEBIT)
                .cardLastFour("12")
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("CARD_INFO_REQUIRED", exception.getErrorCode());
    }

    // --- Currency conversion tests ---

    @Test
    void processPayment_foreignCurrency_convertsToUsd() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("EUR")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(currencyConverter.convertToUsd(new BigDecimal("100.00"), "EUR"))
                .thenReturn(new BigDecimal("108.75"));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });

        PaymentResponse response = paymentService.processPayment(request);
        assertNotNull(response);
        verify(currencyConverter).convertToUsd(new BigDecimal("100.00"), "EUR");
    }

    @Test
    void processPayment_unsupportedCurrency_throwsException() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("XYZ")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(currencyConverter.convertToUsd(new BigDecimal("100.00"), "XYZ"))
                .thenReturn(null);

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("UNSUPPORTED_CURRENCY", exception.getErrorCode());
    }

    // --- Merchant daily limit tests ---

    @Test
    void processPayment_exceedsMerchantDailyLimit_throwsException() {
        testMerchant.setDailyLimit(new BigDecimal("1000.00"));
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("500.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(new BigDecimal("600.00"));

        assertThrows(InsufficientFundsException.class,
                () -> paymentService.processPayment(request));
    }

    @Test
    void processPayment_merchantNoDailyLimit_skipsCheck() {
        testMerchant.setDailyLimit(null);
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });

        PaymentResponse response = paymentService.processPayment(request);
        assertNotNull(response);
    }

    // --- Velocity check ---

    @Test
    void processPayment_creditCard_velocityExceeded_throwsException() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        // Return 10+ recent payments to trigger velocity check
        List<Payment> recentPayments = Collections.nCopies(10, Payment.builder().build());
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(recentPayments);

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("VELOCITY_EXCEEDED", exception.getErrorCode());
    }

    // --- Credit card validation ---

    @Test
    void processPayment_creditCard_missingCardLastFour_throwsException() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("CARD_INFO_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_creditCard_invalidCardLastFour_throwsException() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("42")
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("CARD_INFO_REQUIRED", exception.getErrorCode());
    }

    // --- Notification failure tolerance ---

    @Test
    void processPayment_notificationFails_paymentStillSucceeds() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });
        doThrow(new RuntimeException("Notification error")).when(notificationService)
                .sendPaymentNotification(any(Payment.class));

        PaymentResponse response = paymentService.processPayment(request);
        assertNotNull(response);
    }

    // --- getPaymentByTransactionId ---

    @Test
    void getPaymentByTransactionId_success() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-ABC123")
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();
        when(paymentRepository.findByTransactionId("TXN-ABC123"))
                .thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentByTransactionId("TXN-ABC123");
        assertEquals("TXN-ABC123", response.getTransactionId());
    }

    @Test
    void getPaymentByTransactionId_notFound() {
        when(paymentRepository.findByTransactionId("NONEXISTENT"))
                .thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.getPaymentByTransactionId("NONEXISTENT"));
        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
    }

    // --- getPaymentsByMerchant ---

    @Test
    void getPaymentsByMerchant_success() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-123")
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();
        when(paymentRepository.findByMerchantIdAndStatus(1L, null))
                .thenReturn(List.of(payment));

        List<PaymentResponse> result = paymentService.getPaymentsByMerchant(1L);
        assertEquals(1, result.size());
    }

    @Test
    void getPaymentsByMerchant_empty() {
        when(paymentRepository.findByMerchantIdAndStatus(999L, null))
                .thenReturn(Collections.emptyList());

        List<PaymentResponse> result = paymentService.getPaymentsByMerchant(999L);
        assertTrue(result.isEmpty());
    }

    // --- updatePaymentStatus ---

    @Test
    void updatePaymentStatus_success() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-123")
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.PROCESSING)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        PaymentResponse response = paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED);
        assertNotNull(response);
    }

    @Test
    void updatePaymentStatus_toCompleted_setsCompletedAt() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-123")
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.PROCESSING)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED);

        verify(paymentRepository).save(argThat(p -> p.getCompletedAt() != null));
    }

    @Test
    void updatePaymentStatus_terminalState_throwsException() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-123")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.updatePaymentStatus(1L, PaymentStatus.PROCESSING));
        assertEquals("INVALID_STATE_TRANSITION", exception.getErrorCode());
    }

    @Test
    void updatePaymentStatus_terminalToRefunded_allowed() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-123")
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        PaymentResponse response = paymentService.updatePaymentStatus(1L, PaymentStatus.REFUNDED);
        assertNotNull(response);
    }

    @Test
    void updatePaymentStatus_notFound_throwsException() {
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(PaymentException.class,
                () -> paymentService.updatePaymentStatus(999L, PaymentStatus.COMPLETED));
    }

    // --- Idempotency with empty key ---

    @Test
    void processPayment_emptyIdempotencyKey_treatedAsNew() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .idempotencyKey("")
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });

        PaymentResponse response = paymentService.processPayment(request);
        assertNotNull(response);
        verify(paymentRepository, never()).findByIdempotencyKey(anyString());
    }

    // --- cancelPayment not found ---

    @Test
    void cancelPayment_notFound_throwsException() {
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(PaymentException.class,
                () -> paymentService.cancelPayment(999L));
    }

    // --- getTodaysTotalForMerchant null handling ---

    @Test
    void processPayment_todaysTotalNull_treatsAsZero() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentType(PaymentType.ACH)
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(null);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });

        PaymentResponse response = paymentService.processPayment(request);
        assertNotNull(response);
    }

    // --- getPaymentById success ---

    @Test
    void getPaymentById_success() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-123")
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentById(1L);
        assertEquals("TXN-123", response.getTransactionId());
        assertEquals(new BigDecimal("100.00"), response.getAmount());
    }
}
