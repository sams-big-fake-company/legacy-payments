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
    // Additional coverage: currency handling, payment-type rules, limits, fees,
    // gateway/notification failure handling and read/update operations.
    // ============================

    private void stubActiveMerchant() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
    }

    private void stubNoPaymentsToday() {
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any())).thenReturn(BigDecimal.ZERO);
    }

    private void stubNoRecentPayments() {
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(java.util.Collections.emptyList());
    }

    private void stubSaveEchoesPayment() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static Payment recentPayments(int index) {
        return Payment.builder().id((long) index).transactionId("TXN-" + index).build();
    }

    @Test
    void processPayment_nonUsdCurrencyIsConvertedForLimitChecks() {
        validRequest.setCurrency("EUR");
        stubActiveMerchant();
        when(currencyConverter.convertToUsd(new BigDecimal("99.99"), "EUR"))
                .thenReturn(new BigDecimal("108.7391"));
        stubNoRecentPayments();
        stubNoPaymentsToday();
        stubSaveEchoesPayment();

        PaymentResponse response = paymentService.processPayment(validRequest);

        assertEquals("EUR", response.getCurrency());
        assertEquals(new BigDecimal("99.99"), response.getAmount());
        verify(currencyConverter).convertToUsd(new BigDecimal("99.99"), "EUR");
    }

    @Test
    void processPayment_unsupportedCurrencyIsRejected() {
        validRequest.setCurrency("XYZ");
        stubActiveMerchant();
        when(currencyConverter.convertToUsd(any(), eq("XYZ"))).thenReturn(null);

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));

        assertEquals("UNSUPPORTED_CURRENCY", exception.getErrorCode());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void processPayment_usdSkipsCurrencyConversion() {
        stubActiveMerchant();
        stubNoRecentPayments();
        stubNoPaymentsToday();
        stubSaveEchoesPayment();

        paymentService.processPayment(validRequest);

        verifyNoInteractions(currencyConverter);
    }

    @Test
    void processPayment_nullCurrencySkipsConversion() {
        validRequest.setCurrency(null);
        stubActiveMerchant();
        stubNoRecentPayments();
        stubNoPaymentsToday();
        stubSaveEchoesPayment();

        PaymentResponse response = paymentService.processPayment(validRequest);

        assertNull(response.getCurrency());
        verifyNoInteractions(currencyConverter);
    }

    @Test
    void processPayment_wireWithNullCustomerNameIsRejected() {
        validRequest.setPaymentType(PaymentType.WIRE);
        validRequest.setAmount(new BigDecimal("500.00"));
        validRequest.setCustomerName(null);
        stubActiveMerchant();

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));

        assertEquals("WIRE_NAME_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_debitWithMalformedCardDetailsIsRejected() {
        validRequest.setPaymentType(PaymentType.DEBIT);
        validRequest.setCardLastFour("42");
        stubActiveMerchant();

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));

        assertEquals("CARD_INFO_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_wireBelowMinimumIsRejected() {
        validRequest.setPaymentType(PaymentType.WIRE);
        validRequest.setAmount(new BigDecimal("99.99"));
        validRequest.setCustomerName("Jane Doe");
        stubActiveMerchant();

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));

        assertEquals("WIRE_MINIMUM_NOT_MET", exception.getErrorCode());
    }

    @Test
    void processPayment_wireWithoutCustomerNameIsRejected() {
        validRequest.setPaymentType(PaymentType.WIRE);
        validRequest.setAmount(new BigDecimal("500.00"));
        validRequest.setCustomerName("   ");
        stubActiveMerchant();

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));

        assertEquals("WIRE_NAME_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_wireAtMinimumWithCustomerNameSucceeds() {
        validRequest.setPaymentType(PaymentType.WIRE);
        validRequest.setAmount(new BigDecimal("100.00"));
        validRequest.setCustomerName("Jane Doe");
        stubActiveMerchant();
        stubNoPaymentsToday();
        stubSaveEchoesPayment();

        PaymentResponse response = paymentService.processPayment(validRequest);

        // 100.00 * 0.001 (wire fee) = 0.1000
        assertEquals(new BigDecimal("0.1000"), response.getFeeAmount());
        assertEquals(new BigDecimal("99.9000"), response.getNetAmount());
    }

    @Test
    void processPayment_achOverDailyLimitReportsRemainingHeadroom() {
        validRequest.setPaymentType(PaymentType.ACH);
        validRequest.setAmount(new BigDecimal("5000.00"));
        stubActiveMerchant();
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(new BigDecimal("24000.00"));

        InsufficientFundsException exception = assertThrows(InsufficientFundsException.class,
                () -> paymentService.processPayment(validRequest));

        assertEquals("INSUFFICIENT_FUNDS", exception.getErrorCode());
        assertEquals(new BigDecimal("5000.00"), exception.getRequestedAmount());
        assertEquals(new BigDecimal("1000.00"), exception.getAvailableAmount());
    }

    @Test
    void processPayment_achWithinDailyLimitSucceeds() {
        validRequest.setPaymentType(PaymentType.ACH);
        validRequest.setAmount(new BigDecimal("1000.00"));
        stubActiveMerchant();
        stubNoPaymentsToday();
        stubSaveEchoesPayment();

        PaymentResponse response = paymentService.processPayment(validRequest);

        // 1000.00 * 0.008 (ACH fee) = 8.0000
        assertEquals(new BigDecimal("8.0000"), response.getFeeAmount());
    }

    @Test
    void processPayment_creditCardWithoutCardDetailsIsRejected() {
        validRequest.setCardLastFour(null);
        stubActiveMerchant();

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));

        assertEquals("CARD_INFO_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_creditCardWithMalformedCardDetailsIsRejected() {
        validRequest.setCardLastFour("42");
        stubActiveMerchant();

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));

        assertEquals("CARD_INFO_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_velocityLimitIsEnforced() {
        stubActiveMerchant();
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(java.util.stream.IntStream.range(0, 10)
                        .mapToObj(PaymentServiceImplTest::recentPayments)
                        .collect(java.util.stream.Collectors.toList()));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));

        assertEquals("VELOCITY_EXCEEDED", exception.getErrorCode());
    }

    @Test
    void processPayment_nineRecentTransactionsStayUnderVelocityLimit() {
        stubActiveMerchant();
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(java.util.stream.IntStream.range(0, 9)
                        .mapToObj(PaymentServiceImplTest::recentPayments)
                        .collect(java.util.stream.Collectors.toList()));
        stubNoPaymentsToday();
        stubSaveEchoesPayment();

        assertNotNull(paymentService.processPayment(validRequest));
    }

    @Test
    void processPayment_debitWithoutCardDetailsIsRejected() {
        validRequest.setPaymentType(PaymentType.DEBIT);
        validRequest.setCardLastFour(null);
        stubActiveMerchant();

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));

        assertEquals("CARD_INFO_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_debitSkipsVelocityCheck() {
        validRequest.setPaymentType(PaymentType.DEBIT);
        stubActiveMerchant();
        stubNoPaymentsToday();
        stubSaveEchoesPayment();

        PaymentResponse response = paymentService.processPayment(validRequest);

        // 99.99 * 0.015 (debit fee) = 1.4999 (half up)
        assertEquals(new BigDecimal("1.4999"), response.getFeeAmount());
        verify(paymentRepository, never()).findByMerchantIdAndDateRange(anyLong(), any(), any());
    }

    @Test
    void processPayment_merchantDailyLimitIsEnforced() {
        testMerchant.setDailyLimit(new BigDecimal("120.00"));
        validRequest.setPaymentType(PaymentType.WIRE);
        validRequest.setAmount(new BigDecimal("100.00"));
        validRequest.setCustomerName("Jane Doe");
        stubActiveMerchant();
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(new BigDecimal("50.00"));

        InsufficientFundsException exception = assertThrows(InsufficientFundsException.class,
                () -> paymentService.processPayment(validRequest));

        assertEquals(new BigDecimal("100.00"), exception.getRequestedAmount());
        assertEquals(new BigDecimal("70.00"), exception.getAvailableAmount());
    }

    @Test
    void processPayment_merchantWithoutDailyLimitSkipsLimitCheck() {
        testMerchant.setDailyLimit(null);
        stubActiveMerchant();
        stubNoRecentPayments();
        stubSaveEchoesPayment();

        assertNotNull(paymentService.processPayment(validRequest));

        verify(paymentRepository, never()).sumCompletedAmountByMerchantSince(anyLong(), any());
    }

    @Test
    void processPayment_nullDailyTotalIsTreatedAsZero() {
        stubActiveMerchant();
        stubNoRecentPayments();
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any())).thenReturn(null);
        stubSaveEchoesPayment();

        assertNotNull(paymentService.processPayment(validRequest));
    }

    @Test
    void processPayment_creditCardFeeIsTwoPointNinePercent() {
        stubActiveMerchant();
        stubNoRecentPayments();
        stubNoPaymentsToday();
        stubSaveEchoesPayment();

        PaymentResponse response = paymentService.processPayment(validRequest);

        // 99.99 * 0.029 = 2.89971 -> 2.8997 (half up)
        assertEquals(new BigDecimal("2.8997"), response.getFeeAmount());
        assertEquals(new BigDecimal("97.0903"), response.getNetAmount());
    }

    @Test
    void processPayment_reachesTerminalStatusAndNotifies() {
        stubActiveMerchant();
        stubNoRecentPayments();
        stubNoPaymentsToday();
        stubSaveEchoesPayment();

        PaymentResponse response = paymentService.processPayment(validRequest);

        assertTrue(response.getStatus().isTerminal(),
                "expected a terminal status but was " + response.getStatus());
        verify(notificationService).sendPaymentNotification(any(Payment.class));
    }

    @Test
    void processPayment_notificationFailureDoesNotFailThePayment() {
        stubActiveMerchant();
        stubNoRecentPayments();
        stubNoPaymentsToday();
        stubSaveEchoesPayment();
        doThrow(new RuntimeException("notification service down"))
                .when(notificationService).sendPaymentNotification(any(Payment.class));

        PaymentResponse response = paymentService.processPayment(validRequest);

        assertNotNull(response.getTransactionId());
    }

    @Test
    void processPayment_gatewayFailureIsRecordedOnThePayment() {
        stubActiveMerchant();
        stubNoRecentPayments();
        stubNoPaymentsToday();
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0))
                .thenThrow(new RuntimeException("gateway exploded"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentService.processPayment(validRequest);

        assertEquals(PaymentStatus.FAILED, response.getStatus());
        assertEquals("Processing error: gateway exploded", response.getFailureReason());
    }

    @Test
    void processPayment_blankIdempotencyKeyIsIgnored() {
        validRequest.setIdempotencyKey("");
        stubActiveMerchant();
        stubNoRecentPayments();
        stubNoPaymentsToday();
        stubSaveEchoesPayment();

        assertNotNull(paymentService.processPayment(validRequest));

        verify(paymentRepository, never()).findByIdempotencyKey(any());
    }

    @Test
    void processPayment_unknownIdempotencyKeyProceedsWithNewPayment() {
        validRequest.setIdempotencyKey("idem-new");
        when(paymentRepository.findByIdempotencyKey("idem-new")).thenReturn(Optional.empty());
        stubActiveMerchant();
        stubNoRecentPayments();
        stubNoPaymentsToday();
        stubSaveEchoesPayment();

        PaymentResponse response = paymentService.processPayment(validRequest);

        assertTrue(response.getTransactionId().startsWith("TXN-"));
    }

    @Test
    void getPaymentById_returnsMappedResponse() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-READ")
                .merchantId(1L)
                .amount(new BigDecimal("12.34"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .description("desc")
                .customerEmail("test@example.com")
                .feeAmount(new BigDecimal("0.36"))
                .netAmount(new BigDecimal("11.98"))
                .gatewayReference("GW-1")
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentById(1L);

        assertEquals("TXN-READ", response.getTransactionId());
        assertEquals(new BigDecimal("12.34"), response.getAmount());
        assertEquals(new BigDecimal("0.36"), response.getFeeAmount());
        assertEquals("GW-1", response.getGatewayReference());
        assertEquals("desc", response.getDescription());
    }

    @Test
    void getPaymentByTransactionId_returnsMappedResponse() {
        Payment payment = Payment.builder().id(2L).transactionId("TXN-LOOKUP").build();
        when(paymentRepository.findByTransactionId("TXN-LOOKUP")).thenReturn(Optional.of(payment));

        assertEquals("TXN-LOOKUP", paymentService.getPaymentByTransactionId("TXN-LOOKUP").getTransactionId());
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
                Payment.builder().id(1L).transactionId("TXN-1").build(),
                Payment.builder().id(2L).transactionId("TXN-2").build()));

        java.util.List<PaymentResponse> responses = paymentService.getPaymentsByMerchant(1L);

        assertEquals(2, responses.size());
        assertEquals("TXN-1", responses.get(0).getTransactionId());
        assertEquals("TXN-2", responses.get(1).getTransactionId());
    }

    @Test
    void getPaymentsByMerchant_returnsEmptyListWhenNoPayments() {
        when(paymentRepository.findByMerchantIdAndStatus(1L, null))
                .thenReturn(java.util.Collections.emptyList());

        assertTrue(paymentService.getPaymentsByMerchant(1L).isEmpty());
    }

    @Test
    void updatePaymentStatus_completingSetsCompletedAt() {
        Payment payment = Payment.builder().id(1L).status(PaymentStatus.PROCESSING).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        stubSaveEchoesPayment();

        PaymentResponse response = paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED);

        assertEquals(PaymentStatus.COMPLETED, response.getStatus());
        assertNotNull(response.getCompletedAt());
    }

    @Test
    void updatePaymentStatus_nonCompletedStatusLeavesCompletedAtUnset() {
        Payment payment = Payment.builder().id(1L).status(PaymentStatus.PENDING).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        stubSaveEchoesPayment();

        PaymentResponse response = paymentService.updatePaymentStatus(1L, PaymentStatus.PROCESSING);

        assertEquals(PaymentStatus.PROCESSING, response.getStatus());
        assertNull(response.getCompletedAt());
    }

    @Test
    void updatePaymentStatus_terminalPaymentsCannotTransitionExceptToRefunded() {
        Payment payment = Payment.builder().id(1L).status(PaymentStatus.COMPLETED).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.updatePaymentStatus(1L, PaymentStatus.PENDING));

        assertEquals("INVALID_STATE_TRANSITION", exception.getErrorCode());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void updatePaymentStatus_completedPaymentCanBeRefunded() {
        Payment payment = Payment.builder().id(1L).status(PaymentStatus.COMPLETED).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        stubSaveEchoesPayment();

        assertEquals(PaymentStatus.REFUNDED,
                paymentService.updatePaymentStatus(1L, PaymentStatus.REFUNDED).getStatus());
    }

    @Test
    void updatePaymentStatus_throwsWhenPaymentMissing() {
        when(paymentRepository.findById(42L)).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.updatePaymentStatus(42L, PaymentStatus.COMPLETED));

        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void cancelPayment_throwsWhenPaymentMissing() {
        when(paymentRepository.findById(42L)).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.cancelPayment(42L));

        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void cancelPayment_recordsCancellationReason() {
        Payment payment = Payment.builder().id(1L).transactionId("TXN-C").status(PaymentStatus.PENDING).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        stubSaveEchoesPayment();

        paymentService.cancelPayment(1L);

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        assertEquals("Cancelled by user", payment.getFailureReason());
    }
}
