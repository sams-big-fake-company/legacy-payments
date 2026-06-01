package com.bigfake.payments.service;

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
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.bigfake.payments.exception.InsufficientFundsException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PaymentServiceImpl.
 *
 * TODO: PAY-3455 - Add more edge case tests
 * TODO: PAY-3456 - Add integration tests with real database
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

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
    private PaymentRequest validRequest;

    @BeforeEach
    void setUp() {
        testMerchant = Merchant.builder()
                .id(1L)
                .merchantCode("MERCH001")
                .businessName("Test Store")
                .isActive(true)
                .dailyLimit(new BigDecimal("100000.00"))
                .build();

        validRequest = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("99.99"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .customerEmail("test@example.com")
                .build();
    }

    @Test
    void processPayment_success() {
        // Given
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(java.util.Collections.emptyList());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });

        // When
        PaymentResponse response = paymentService.processPayment(validRequest);

        // Then
        assertNotNull(response);
        assertNotNull(response.getTransactionId());
        assertTrue(response.getTransactionId().startsWith("TXN-"));
        assertEquals(new BigDecimal("99.99"), response.getAmount());
        verify(paymentRepository, atLeast(2)).save(any(Payment.class));
    }

    @Test
    void processPayment_merchantNotFound() {
        // Given
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());

        // When/Then
        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));
        assertEquals("MERCHANT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void processPayment_merchantInactive() {
        // Given
        testMerchant.setIsActive(false);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        // When/Then
        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));
        assertEquals("MERCHANT_INACTIVE", exception.getErrorCode());
    }

    @Test
    void processPayment_amountTooLow() {
        // Given
        validRequest.setAmount(new BigDecimal("0.01"));
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        // When/Then
        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));
        assertEquals("AMOUNT_TOO_LOW", exception.getErrorCode());
    }

    @Test
    void processPayment_amountTooHigh() {
        // Given
        validRequest.setAmount(new BigDecimal("99999.99"));
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        // When/Then
        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));
        assertEquals("AMOUNT_TOO_HIGH", exception.getErrorCode());
    }

    @Test
    void processPayment_idempotentRequest() {
        // Given
        validRequest.setIdempotencyKey("idem-key-123");
        Payment existingPayment = Payment.builder()
                .id(1L)
                .transactionId("TXN-EXISTING123")
                .merchantId(1L)
                .amount(new BigDecimal("99.99"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .idempotencyKey("idem-key-123")
                .build();
        when(paymentRepository.findByIdempotencyKey("idem-key-123"))
                .thenReturn(Optional.of(existingPayment));

        // When
        PaymentResponse response = paymentService.processPayment(validRequest);

        // Then
        assertNotNull(response);
        assertEquals("TXN-EXISTING123", response.getTransactionId());
        verify(merchantRepository, never()).findById(anyLong());
    }

    @Test
    void getPaymentById_notFound() {
        // Given
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(PaymentException.class, () -> paymentService.getPaymentById(999L));
    }

    @Test
    void cancelPayment_success() {
        // Given
        Payment pendingPayment = Payment.builder()
                .id(1L)
                .transactionId("TXN-CANCEL123")
                .status(PaymentStatus.PENDING)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(pendingPayment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(pendingPayment);

        // When
        paymentService.cancelPayment(1L);

        // Then
        verify(paymentRepository).save(argThat(p -> p.getStatus() == PaymentStatus.FAILED));
    }

    @Test
    void cancelPayment_notPending() {
        // Given
        Payment completedPayment = Payment.builder()
                .id(1L)
                .status(PaymentStatus.COMPLETED)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));

        // When/Then
        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.cancelPayment(1L));
        assertEquals("CANNOT_CANCEL", exception.getErrorCode());
    }

    @Test
    void cancelPayment_notFound() {
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(PaymentException.class, () -> paymentService.cancelPayment(999L));
    }

    // --- Currency conversion path ---

    @Test
    void processPayment_foreignCurrency_convertsToUsd() {
        validRequest.setCurrency("EUR");
        validRequest.setAmount(new BigDecimal("100.00"));

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(currencyConverter.convertToUsd(any(), eq("EUR"))).thenReturn(new BigDecimal("108.75"));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });

        PaymentResponse response = paymentService.processPayment(validRequest);
        assertNotNull(response);
        verify(currencyConverter).convertToUsd(any(), eq("EUR"));
    }

    @Test
    void processPayment_unsupportedCurrency_throwsException() {
        validRequest.setCurrency("XYZ");

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(currencyConverter.convertToUsd(any(), eq("XYZ"))).thenReturn(null);

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));
        assertEquals("UNSUPPORTED_CURRENCY", ex.getErrorCode());
    }

    // --- Wire transfer validation ---

    @Test
    void processPayment_wireTransfer_belowMinimum_throwsException() {
        validRequest.setPaymentType(PaymentType.WIRE);
        validRequest.setAmount(new BigDecimal("50.00"));
        validRequest.setCustomerName("John Doe");

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));
        assertEquals("WIRE_MINIMUM_NOT_MET", ex.getErrorCode());
    }

    @Test
    void processPayment_wireTransfer_missingCustomerName_throwsException() {
        validRequest.setPaymentType(PaymentType.WIRE);
        validRequest.setAmount(new BigDecimal("200.00"));
        validRequest.setCustomerName(null);

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));
        assertEquals("WIRE_NAME_REQUIRED", ex.getErrorCode());
    }

    @Test
    void processPayment_wireTransfer_emptyCustomerName_throwsException() {
        validRequest.setPaymentType(PaymentType.WIRE);
        validRequest.setAmount(new BigDecimal("200.00"));
        validRequest.setCustomerName("   ");

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));
        assertEquals("WIRE_NAME_REQUIRED", ex.getErrorCode());
    }

    @Test
    void processPayment_wireTransfer_success() {
        validRequest.setPaymentType(PaymentType.WIRE);
        validRequest.setAmount(new BigDecimal("500.00"));
        validRequest.setCustomerName("John Doe");

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });

        PaymentResponse response = paymentService.processPayment(validRequest);
        assertNotNull(response);
    }

    // --- ACH daily limit ---

    @Test
    void processPayment_ach_dailyLimitExceeded_throwsException() {
        validRequest.setPaymentType(PaymentType.ACH);
        validRequest.setAmount(new BigDecimal("1000.00"));

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(new BigDecimal("24500.00"));

        assertThrows(InsufficientFundsException.class,
                () -> paymentService.processPayment(validRequest));
    }

    @Test
    void processPayment_ach_withinLimit_success() {
        validRequest.setPaymentType(PaymentType.ACH);
        validRequest.setAmount(new BigDecimal("100.00"));

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });

        PaymentResponse response = paymentService.processPayment(validRequest);
        assertNotNull(response);
    }

    // --- Credit card: missing card info ---

    @Test
    void processPayment_creditCard_missingCardLastFour_throwsException() {
        validRequest.setPaymentType(PaymentType.CREDIT_CARD);
        validRequest.setCardLastFour(null);

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));
        assertEquals("CARD_INFO_REQUIRED", ex.getErrorCode());
    }

    @Test
    void processPayment_creditCard_wrongLengthCardLastFour_throwsException() {
        validRequest.setPaymentType(PaymentType.CREDIT_CARD);
        validRequest.setCardLastFour("12");

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));
        assertEquals("CARD_INFO_REQUIRED", ex.getErrorCode());
    }

    // --- Credit card: velocity check ---

    @Test
    void processPayment_creditCard_velocityExceeded_throwsException() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        // Return 10 recent payments to trigger velocity check
        List<Payment> recentPayments = Collections.nCopies(10, Payment.builder().build());
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(recentPayments);

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));
        assertEquals("VELOCITY_EXCEEDED", ex.getErrorCode());
    }

    // --- Debit card validation ---

    @Test
    void processPayment_debit_missingCardLastFour_throwsException() {
        validRequest.setPaymentType(PaymentType.DEBIT);
        validRequest.setCardLastFour(null);

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));
        assertEquals("CARD_INFO_REQUIRED", ex.getErrorCode());
    }

    @Test
    void processPayment_debit_success() {
        validRequest.setPaymentType(PaymentType.DEBIT);
        validRequest.setCardLastFour("5678");

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });

        PaymentResponse response = paymentService.processPayment(validRequest);
        assertNotNull(response);
    }

    // --- Merchant daily limit ---

    @Test
    void processPayment_merchantDailyLimitExceeded_throwsException() {
        testMerchant.setDailyLimit(new BigDecimal("1000.00"));
        validRequest.setAmount(new BigDecimal("500.00"));

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(new BigDecimal("800.00"));
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());

        assertThrows(InsufficientFundsException.class,
                () -> paymentService.processPayment(validRequest));
    }

    @Test
    void processPayment_merchantNoDailyLimit_success() {
        testMerchant.setDailyLimit(null);

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });

        PaymentResponse response = paymentService.processPayment(validRequest);
        assertNotNull(response);
    }

    // --- getPaymentByTransactionId ---

    @Test
    void getPaymentByTransactionId_found_returnsResponse() {
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
    void getPaymentByTransactionId_notFound_throwsException() {
        when(paymentRepository.findByTransactionId("TXN-MISSING"))
                .thenReturn(Optional.empty());

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.getPaymentByTransactionId("TXN-MISSING"));
        assertEquals("PAYMENT_NOT_FOUND", ex.getErrorCode());
    }

    // --- getPaymentsByMerchant ---

    @Test
    void getPaymentsByMerchant_returnsList() {
        List<Payment> payments = List.of(
                Payment.builder().id(1L).transactionId("TXN-1").merchantId(1L)
                        .amount(new BigDecimal("50.00")).currency("USD")
                        .status(PaymentStatus.COMPLETED).paymentType(PaymentType.CREDIT_CARD).build(),
                Payment.builder().id(2L).transactionId("TXN-2").merchantId(1L)
                        .amount(new BigDecimal("75.00")).currency("USD")
                        .status(PaymentStatus.PENDING).paymentType(PaymentType.ACH).build()
        );
        when(paymentRepository.findByMerchantIdAndStatus(1L, null)).thenReturn(payments);

        List<PaymentResponse> result = paymentService.getPaymentsByMerchant(1L);
        assertEquals(2, result.size());
    }

    @Test
    void getPaymentsByMerchant_noPayments_returnsEmptyList() {
        when(paymentRepository.findByMerchantIdAndStatus(1L, null))
                .thenReturn(Collections.emptyList());

        List<PaymentResponse> result = paymentService.getPaymentsByMerchant(1L);
        assertTrue(result.isEmpty());
    }

    // --- updatePaymentStatus ---

    @Test
    void updatePaymentStatus_validTransition_success() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-TEST")
                .status(PaymentStatus.PENDING)
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        PaymentResponse response = paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED);
        assertNotNull(response);
    }

    @Test
    void updatePaymentStatus_terminalToNonRefunded_throwsException() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-TEST")
                .status(PaymentStatus.COMPLETED)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.updatePaymentStatus(1L, PaymentStatus.PROCESSING));
        assertEquals("INVALID_STATE_TRANSITION", ex.getErrorCode());
    }

    @Test
    void updatePaymentStatus_terminalToRefunded_succeeds() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-TEST")
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .status(PaymentStatus.COMPLETED)
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

    // --- getPaymentById: success ---

    @Test
    void getPaymentById_found_returnsResponse() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-FOUND001")
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentById(1L);
        assertEquals("TXN-FOUND001", response.getTransactionId());
    }

    // --- Notification failure during processPayment ---

    @Test
    void processPayment_notificationFailure_doesNotFailPayment() {
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
        doThrow(new RuntimeException("Notification service down"))
                .when(notificationService).sendPaymentNotification(any(Payment.class));

        PaymentResponse response = paymentService.processPayment(validRequest);
        assertNotNull(response);
    }

    // --- Idempotency: null and empty keys ---

    @Test
    void processPayment_nullIdempotencyKey_processesFresh() {
        validRequest.setIdempotencyKey(null);

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

        PaymentResponse response = paymentService.processPayment(validRequest);
        assertNotNull(response);
        verify(paymentRepository, never()).findByIdempotencyKey(any());
    }

    @Test
    void processPayment_emptyIdempotencyKey_processesFresh() {
        validRequest.setIdempotencyKey("");

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

        PaymentResponse response = paymentService.processPayment(validRequest);
        assertNotNull(response);
        verify(paymentRepository, never()).findByIdempotencyKey(any());
    }

    // --- getTodaysTotalForMerchant returns null ---

    @Test
    void processPayment_nullDailyTotal_treatsAsZero() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(null);
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });

        PaymentResponse response = paymentService.processPayment(validRequest);
        assertNotNull(response);
    }
}
