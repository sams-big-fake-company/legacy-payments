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
import java.util.Optional;

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

    // ============================
    // Helpers for the cases below
    // ============================

    private void stubSaveEchoingArgument() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            if (p.getId() == null) {
                p.setId(1L);
            }
            return p;
        });
    }

    private void stubMerchantFoundWithNoHistory() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any())).thenReturn(BigDecimal.ZERO);
    }

    private static Payment storedPayment(Long id, String transactionId, PaymentStatus status) {
        return Payment.builder()
                .id(id)
                .transactionId(transactionId)
                .merchantId(1L)
                .amount(new BigDecimal("99.99"))
                .currency("USD")
                .status(status)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();
    }

    // ============================
    // Amount, currency and fees
    // ============================

    @Test
    void processPayment_acceptsTheMinimumAndMaximumAllowedAmounts() {
        stubMerchantFoundWithNoHistory();
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(java.util.Collections.emptyList());
        stubSaveEchoingArgument();

        validRequest.setAmount(new BigDecimal("0.50"));
        assertNotNull(paymentService.processPayment(validRequest));

        validRequest.setAmount(new BigDecimal("50000.00"));
        assertNotNull(paymentService.processPayment(validRequest));
    }

    @Test
    void processPayment_calculatesFeeAndNetAmountFromThePaymentTypeRate() {
        stubMerchantFoundWithNoHistory();
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(java.util.Collections.emptyList());
        stubSaveEchoingArgument();
        validRequest.setAmount(new BigDecimal("100.00"));

        PaymentResponse response = paymentService.processPayment(validRequest);

        // CREDIT_CARD -> 2.9%
        assertEquals(new BigDecimal("2.9000"), response.getFeeAmount());
        assertEquals(new BigDecimal("97.1000"), response.getNetAmount());
    }

    @Test
    void processPayment_convertsNonUsdAmountsBeforeCheckingLimits() {
        stubMerchantFoundWithNoHistory();
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(java.util.Collections.emptyList());
        when(currencyConverter.convertToUsd(new BigDecimal("99.99"), "EUR"))
                .thenReturn(new BigDecimal("108.74"));
        stubSaveEchoingArgument();
        validRequest.setCurrency("EUR");

        PaymentResponse response = paymentService.processPayment(validRequest);

        assertEquals("EUR", response.getCurrency());
        // The stored amount stays in the original currency; only limit checks use USD.
        assertEquals(new BigDecimal("99.99"), response.getAmount());
        verify(currencyConverter).convertToUsd(new BigDecimal("99.99"), "EUR");
    }

    @Test
    void processPayment_rejectsCurrenciesTheConverterCannotHandle() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(currencyConverter.convertToUsd(any(), eq("XYZ"))).thenReturn(null);
        validRequest.setCurrency("XYZ");

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));

        assertEquals("UNSUPPORTED_CURRENCY", exception.getErrorCode());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    // ============================
    // Payment type specific rules
    // ============================

    @Test
    void processPayment_rejectsWireTransfersBelowTheWireMinimum() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        validRequest.setPaymentType(PaymentType.WIRE);
        validRequest.setAmount(new BigDecimal("99.99"));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));

        assertEquals("WIRE_MINIMUM_NOT_MET", exception.getErrorCode());
    }

    @Test
    void processPayment_rejectsWireTransfersWithoutACustomerName() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        validRequest.setPaymentType(PaymentType.WIRE);
        validRequest.setAmount(new BigDecimal("500.00"));
        validRequest.setCustomerName("   ");

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));

        assertEquals("WIRE_NAME_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_acceptsWireTransfersAtTheMinimumWithACustomerName() {
        stubMerchantFoundWithNoHistory();
        stubSaveEchoingArgument();
        validRequest.setPaymentType(PaymentType.WIRE);
        validRequest.setAmount(new BigDecimal("100.00"));
        validRequest.setCustomerName("Ada Lovelace");

        PaymentResponse response = paymentService.processPayment(validRequest);

        // WIRE -> 0.1%
        assertEquals(new BigDecimal("0.1000"), response.getFeeAmount());
    }

    @Test
    void processPayment_rejectsAchPaymentsThatBreachTheAchDailyLimit() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(new BigDecimal("24950.00"));
        validRequest.setPaymentType(PaymentType.ACH);
        validRequest.setAmount(new BigDecimal("100.00"));

        InsufficientFundsException exception = assertThrows(InsufficientFundsException.class,
                () -> paymentService.processPayment(validRequest));

        assertEquals("INSUFFICIENT_FUNDS", exception.getErrorCode());
        assertEquals(new BigDecimal("100.00"), exception.getRequestedAmount());
        assertEquals(new BigDecimal("50.00"), exception.getAvailableAmount());
    }

    @Test
    void processPayment_acceptsAchPaymentsWithinTheAchDailyLimit() {
        stubMerchantFoundWithNoHistory();
        stubSaveEchoingArgument();
        validRequest.setPaymentType(PaymentType.ACH);
        validRequest.setAmount(new BigDecimal("100.00"));

        // ACH -> 0.8%
        assertEquals(new BigDecimal("0.8000"), paymentService.processPayment(validRequest).getFeeAmount());
    }

    @Test
    void processPayment_treatsAMissingTotalAsZeroSpentToday() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any())).thenReturn(null);
        stubSaveEchoingArgument();
        validRequest.setPaymentType(PaymentType.ACH);

        assertNotNull(paymentService.processPayment(validRequest));
    }

    @Test
    void processPayment_requiresCardLastFourForCreditCardPayments() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        validRequest.setCardLastFour(null);

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));

        assertEquals("CARD_INFO_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_requiresCardLastFourForDebitPayments() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        validRequest.setPaymentType(PaymentType.DEBIT);
        validRequest.setCardLastFour("42");

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));

        assertEquals("CARD_INFO_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_acceptsDebitPaymentsWithCardDetails() {
        stubMerchantFoundWithNoHistory();
        stubSaveEchoingArgument();
        validRequest.setPaymentType(PaymentType.DEBIT);
        validRequest.setAmount(new BigDecimal("100.00"));

        // DEBIT -> 1.5%
        assertEquals(new BigDecimal("1.5000"), paymentService.processPayment(validRequest).getFeeAmount());
    }

    @Test
    void processPayment_rejectsCreditCardPaymentsThatBreachTheVelocityLimit() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(java.util.Collections.nCopies(10, storedPayment(2L, "TXN-RECENT", PaymentStatus.COMPLETED)));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));

        assertEquals("VELOCITY_EXCEEDED", exception.getErrorCode());
    }

    // ============================
    // Merchant limits and notifications
    // ============================

    @Test
    void processPayment_rejectsPaymentsThatBreachTheMerchantDailyLimit() {
        testMerchant.setDailyLimit(new BigDecimal("120.00"));
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(java.util.Collections.emptyList());
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(new BigDecimal("50.00"));

        InsufficientFundsException exception = assertThrows(InsufficientFundsException.class,
                () -> paymentService.processPayment(validRequest));

        assertEquals(new BigDecimal("70.00"), exception.getAvailableAmount());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void processPayment_skipsTheDailyLimitCheckWhenTheMerchantHasNoLimit() {
        testMerchant.setDailyLimit(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(java.util.Collections.emptyList());
        stubSaveEchoingArgument();

        assertNotNull(paymentService.processPayment(validRequest));
        verify(paymentRepository, never()).sumCompletedAmountByMerchantSince(anyLong(), any());
    }

    @Test
    void processPayment_ignoresAnEmptyIdempotencyKey() {
        validRequest.setIdempotencyKey("");
        stubMerchantFoundWithNoHistory();
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(java.util.Collections.emptyList());
        stubSaveEchoingArgument();

        assertNotNull(paymentService.processPayment(validRequest));
        verify(paymentRepository, never()).findByIdempotencyKey(any());
    }

    @Test
    void processPayment_succeedsEvenWhenTheNotificationServiceFails() {
        stubMerchantFoundWithNoHistory();
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(java.util.Collections.emptyList());
        stubSaveEchoingArgument();
        doThrow(new RuntimeException("notification bus down"))
                .when(notificationService).sendPaymentNotification(any(Payment.class));

        assertNotNull(paymentService.processPayment(validRequest).getTransactionId());
        verify(notificationService).sendPaymentNotification(any(Payment.class));
    }

    @Test
    void processPayment_marksThePaymentFailedWhenPersistenceThrowsDuringGatewayProcessing() {
        stubMerchantFoundWithNoHistory();
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(java.util.Collections.emptyList());
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> {
                    Payment p = invocation.getArgument(0);
                    p.setId(1L);
                    return p;
                })
                .thenThrow(new RuntimeException("db unavailable"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentService.processPayment(validRequest);

        assertEquals(PaymentStatus.FAILED, response.getStatus());
        assertEquals("Processing error: db unavailable", response.getFailureReason());
    }

    // ============================
    // Reads and status transitions
    // ============================

    @Test
    void getPaymentById_returnsTheMappedPayment() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(storedPayment(1L, "TXN-READ1", PaymentStatus.COMPLETED)));

        PaymentResponse response = paymentService.getPaymentById(1L);

        assertEquals("TXN-READ1", response.getTransactionId());
        assertEquals(PaymentStatus.COMPLETED, response.getStatus());
    }

    @Test
    void getPaymentByTransactionId_returnsTheMappedPayment() {
        when(paymentRepository.findByTransactionId("TXN-READ2"))
                .thenReturn(Optional.of(storedPayment(2L, "TXN-READ2", PaymentStatus.PENDING)));

        assertEquals(2L, paymentService.getPaymentByTransactionId("TXN-READ2").getId());
    }

    @Test
    void getPaymentByTransactionId_throwsWhenMissing() {
        when(paymentRepository.findByTransactionId("TXN-MISSING")).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.getPaymentByTransactionId("TXN-MISSING"));

        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void getPaymentsByMerchant_mapsEveryPayment() {
        when(paymentRepository.findByMerchantIdAndStatus(1L, null)).thenReturn(java.util.List.of(
                storedPayment(1L, "TXN-A", PaymentStatus.COMPLETED),
                storedPayment(2L, "TXN-B", PaymentStatus.FAILED)));

        java.util.List<PaymentResponse> responses = paymentService.getPaymentsByMerchant(1L);

        assertEquals(2, responses.size());
        assertEquals("TXN-A", responses.get(0).getTransactionId());
        assertEquals("TXN-B", responses.get(1).getTransactionId());
    }

    @Test
    void getPaymentsByMerchant_returnsAnEmptyListWhenThereAreNoPayments() {
        when(paymentRepository.findByMerchantIdAndStatus(1L, null)).thenReturn(java.util.Collections.emptyList());

        assertTrue(paymentService.getPaymentsByMerchant(1L).isEmpty());
    }

    @Test
    void updatePaymentStatus_setsCompletedAtWhenCompleting() {
        Payment pending = storedPayment(1L, "TXN-UPD1", PaymentStatus.PENDING);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(pending));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED);

        assertEquals(PaymentStatus.COMPLETED, response.getStatus());
        assertNotNull(response.getCompletedAt());
    }

    @Test
    void updatePaymentStatus_allowsRefundingATerminalPayment() {
        Payment completed = storedPayment(1L, "TXN-UPD2", PaymentStatus.COMPLETED);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completed));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(PaymentStatus.REFUNDED,
                paymentService.updatePaymentStatus(1L, PaymentStatus.REFUNDED).getStatus());
    }

    @Test
    void updatePaymentStatus_rejectsOtherTransitionsOutOfATerminalState() {
        Payment failed = storedPayment(1L, "TXN-UPD3", PaymentStatus.FAILED);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(failed));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED));

        assertEquals("INVALID_STATE_TRANSITION", exception.getErrorCode());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void updatePaymentStatus_throwsWhenThePaymentIsMissing() {
        when(paymentRepository.findById(404L)).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.updatePaymentStatus(404L, PaymentStatus.COMPLETED));

        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void cancelPayment_throwsWhenThePaymentIsMissing() {
        when(paymentRepository.findById(404L)).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.cancelPayment(404L));

        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
    }
}
