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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
    // Helpers for the tests below
    // ============================

    /** Makes paymentRepository.save() echo the entity back with an id, as JPA would. */
    private void stubPaymentSaveEchoingArgument() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(1L);
            return payment;
        });
    }

    private void stubMerchantFound() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
    }

    private void stubTodaysTotal(String total) {
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(total == null ? null : new BigDecimal(total));
    }

    private void stubNoRecentPayments() {
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
    }

    private Payment capturePersistedPayment() {
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    // ============================
    // Amount and idempotency rules
    // ============================

    @Test
    void processPayment_skipsIdempotencyLookupForEmptyKey() {
        validRequest.setIdempotencyKey("");
        stubMerchantFound();
        stubTodaysTotal("0");
        stubNoRecentPayments();
        stubPaymentSaveEchoingArgument();

        paymentService.processPayment(validRequest);

        verify(paymentRepository, never()).findByIdempotencyKey(anyString());
    }

    @Test
    void processPayment_processesNormallyWhenIdempotencyKeyIsUnseen() {
        validRequest.setIdempotencyKey("fresh-key");
        when(paymentRepository.findByIdempotencyKey("fresh-key")).thenReturn(Optional.empty());
        stubMerchantFound();
        stubTodaysTotal("0");
        stubNoRecentPayments();
        stubPaymentSaveEchoingArgument();

        PaymentResponse response = paymentService.processPayment(validRequest);

        assertTrue(response.getTransactionId().startsWith("TXN-"));
        assertEquals("fresh-key", capturePersistedPayment().getIdempotencyKey());
    }

    @Test
    void processPayment_acceptsAmountAtMinimumBoundary() {
        validRequest.setAmount(new BigDecimal("0.50"));
        stubMerchantFound();
        stubTodaysTotal("0");
        stubNoRecentPayments();
        stubPaymentSaveEchoingArgument();

        assertEquals(new BigDecimal("0.50"), paymentService.processPayment(validRequest).getAmount());
    }

    @Test
    void processPayment_acceptsAmountAtMaximumBoundary() {
        validRequest.setAmount(new BigDecimal("50000.00"));
        stubMerchantFound();
        stubTodaysTotal("0");
        stubNoRecentPayments();
        stubPaymentSaveEchoingArgument();

        assertEquals(new BigDecimal("50000.00"), paymentService.processPayment(validRequest).getAmount());
    }

    // ============================
    // Currency normalization
    // ============================

    @Test
    void processPayment_convertsNonUsdAmountForLimitChecks() {
        validRequest.setCurrency("EUR");
        validRequest.setAmount(new BigDecimal("100.00"));
        when(currencyConverter.convertToUsd(new BigDecimal("100.00"), "EUR"))
                .thenReturn(new BigDecimal("108.75"));
        stubMerchantFound();
        stubTodaysTotal("0");
        stubNoRecentPayments();
        stubPaymentSaveEchoingArgument();

        PaymentResponse response = paymentService.processPayment(validRequest);

        // The stored amount stays in the original currency; only limit checks use USD.
        assertEquals(new BigDecimal("100.00"), response.getAmount());
        assertEquals("EUR", response.getCurrency());
        verify(currencyConverter).convertToUsd(new BigDecimal("100.00"), "EUR");
    }

    @Test
    void processPayment_unsupportedCurrency() {
        validRequest.setCurrency("XYZ");
        when(currencyConverter.convertToUsd(any(), eq("XYZ"))).thenReturn(null);
        stubMerchantFound();

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));

        assertEquals("UNSUPPORTED_CURRENCY", exception.getErrorCode());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void processPayment_doesNotConvertWhenCurrencyIsUsd() {
        stubMerchantFound();
        stubTodaysTotal("0");
        stubNoRecentPayments();
        stubPaymentSaveEchoingArgument();

        paymentService.processPayment(validRequest);

        verifyNoInteractions(currencyConverter);
    }

    @Test
    void processPayment_wireMinimumIsCheckedAgainstConvertedAmount() {
        validRequest.setPaymentType(PaymentType.WIRE);
        validRequest.setCardLastFour(null);
        validRequest.setCustomerName("Jane Doe");
        validRequest.setCurrency("JPY");
        validRequest.setAmount(new BigDecimal("10000.00"));
        // 10,000 JPY is only ~$71, below the $100 wire minimum.
        when(currencyConverter.convertToUsd(new BigDecimal("10000.00"), "JPY"))
                .thenReturn(new BigDecimal("71.00"));
        stubMerchantFound();

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));

        assertEquals("WIRE_MINIMUM_NOT_MET", exception.getErrorCode());
    }

    // ============================
    // Payment type specific rules
    // ============================

    @Test
    void processPayment_wireBelowMinimum() {
        validRequest.setPaymentType(PaymentType.WIRE);
        validRequest.setCardLastFour(null);
        validRequest.setCustomerName("Jane Doe");
        validRequest.setAmount(new BigDecimal("99.99"));
        stubMerchantFound();

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));

        assertEquals("WIRE_MINIMUM_NOT_MET", exception.getErrorCode());
    }

    @Test
    void processPayment_wireRequiresCustomerName() {
        validRequest.setPaymentType(PaymentType.WIRE);
        validRequest.setCardLastFour(null);
        validRequest.setAmount(new BigDecimal("500.00"));
        validRequest.setCustomerName("   ");
        stubMerchantFound();

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));

        assertEquals("WIRE_NAME_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_wireSucceedsAtMinimumWithCustomerName() {
        validRequest.setPaymentType(PaymentType.WIRE);
        validRequest.setCardLastFour(null);
        validRequest.setCustomerName("Jane Doe");
        validRequest.setAmount(new BigDecimal("100.00"));
        stubMerchantFound();
        stubTodaysTotal("0");
        stubPaymentSaveEchoingArgument();

        PaymentResponse response = paymentService.processPayment(validRequest);

        // WIRE fee is 0.1%.
        assertEquals(new BigDecimal("0.1000"), response.getFeeAmount());
        assertEquals(new BigDecimal("99.9000"), response.getNetAmount());
    }

    @Test
    void processPayment_achWithinDailyLimit() {
        validRequest.setPaymentType(PaymentType.ACH);
        validRequest.setCardLastFour(null);
        validRequest.setAmount(new BigDecimal("1000.00"));
        stubMerchantFound();
        stubTodaysTotal("24000.00");
        stubPaymentSaveEchoingArgument();

        // 24,000 + 1,000 == the 25,000 ACH daily limit, so this is still allowed.
        assertEquals(new BigDecimal("1000.00"), paymentService.processPayment(validRequest).getAmount());
    }

    @Test
    void processPayment_achDailyLimitExceeded() {
        validRequest.setPaymentType(PaymentType.ACH);
        validRequest.setCardLastFour(null);
        validRequest.setAmount(new BigDecimal("2000.00"));
        stubMerchantFound();
        stubTodaysTotal("24000.00");

        InsufficientFundsException exception = assertThrows(InsufficientFundsException.class,
                () -> paymentService.processPayment(validRequest));

        assertEquals("INSUFFICIENT_FUNDS", exception.getErrorCode());
        assertEquals(new BigDecimal("2000.00"), exception.getRequestedAmount());
        assertEquals(new BigDecimal("1000.00"), exception.getAvailableAmount());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void processPayment_creditCardRequiresCardLastFour() {
        validRequest.setCardLastFour(null);
        stubMerchantFound();

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));

        assertEquals("CARD_INFO_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_creditCardRejectsMalformedCardLastFour() {
        validRequest.setCardLastFour("123");
        stubMerchantFound();

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));

        assertEquals("CARD_INFO_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_velocityExceeded() {
        stubMerchantFound();
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.nCopies(10, Payment.builder().id(2L).build()));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));

        assertEquals("VELOCITY_EXCEEDED", exception.getErrorCode());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void processPayment_velocityAllowsNineRecentTransactions() {
        stubMerchantFound();
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.nCopies(9, Payment.builder().id(2L).build()));
        stubTodaysTotal("0");
        stubPaymentSaveEchoingArgument();

        assertNotNull(paymentService.processPayment(validRequest).getTransactionId());
    }

    @Test
    void processPayment_debitRequiresCardLastFour() {
        validRequest.setPaymentType(PaymentType.DEBIT);
        validRequest.setCardLastFour(null);
        stubMerchantFound();

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));

        assertEquals("CARD_INFO_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_debitSkipsVelocityCheck() {
        validRequest.setPaymentType(PaymentType.DEBIT);
        stubMerchantFound();
        stubTodaysTotal("0");
        stubPaymentSaveEchoingArgument();

        PaymentResponse response = paymentService.processPayment(validRequest);

        // DEBIT fee is 1.5% of 99.99.
        assertEquals(new BigDecimal("1.4999"), response.getFeeAmount());
        verify(paymentRepository, never()).findByMerchantIdAndDateRange(anyLong(), any(), any());
    }

    // ============================
    // Merchant daily limit
    // ============================

    @Test
    void processPayment_merchantDailyLimitExceeded() {
        stubMerchantFound();
        stubNoRecentPayments();
        stubTodaysTotal("99950.00");

        InsufficientFundsException exception = assertThrows(InsufficientFundsException.class,
                () -> paymentService.processPayment(validRequest));

        assertEquals(new BigDecimal("99.99"), exception.getRequestedAmount());
        assertEquals(new BigDecimal("50.00"), exception.getAvailableAmount());
    }

    @Test
    void processPayment_skipsMerchantLimitCheckWhenNoLimitConfigured() {
        testMerchant.setDailyLimit(null);
        stubMerchantFound();
        stubNoRecentPayments();
        stubPaymentSaveEchoingArgument();

        assertNotNull(paymentService.processPayment(validRequest));
        verify(paymentRepository, never()).sumCompletedAmountByMerchantSince(anyLong(), any());
    }

    @Test
    void processPayment_treatsNullDailyTotalAsZero() {
        stubMerchantFound();
        stubNoRecentPayments();
        stubTodaysTotal(null);
        stubPaymentSaveEchoingArgument();

        assertNotNull(paymentService.processPayment(validRequest).getTransactionId());
    }

    // ============================
    // Persistence, gateway and notification
    // ============================

    @Test
    void processPayment_calculatesCreditCardFeeAndNetAmount() {
        stubMerchantFound();
        stubTodaysTotal("0");
        stubNoRecentPayments();
        stubPaymentSaveEchoingArgument();

        PaymentResponse response = paymentService.processPayment(validRequest);

        // 99.99 * 2.9% = 2.8997 (4dp, HALF_UP)
        assertEquals(new BigDecimal("2.8997"), response.getFeeAmount());
        assertEquals(new BigDecimal("97.0903"), response.getNetAmount());
    }

    @Test
    void processPayment_persistsRequestDetailsOnTheEntity() {
        validRequest.setDescription("Order #1234");
        validRequest.setCustomerName("Jane Doe");
        validRequest.setMetadata("{\"channel\":\"web\"}");
        stubMerchantFound();
        stubTodaysTotal("0");
        stubNoRecentPayments();
        stubPaymentSaveEchoingArgument();

        paymentService.processPayment(validRequest);

        Payment persisted = capturePersistedPayment();
        assertEquals(1L, persisted.getMerchantId());
        assertEquals("Order #1234", persisted.getDescription());
        assertEquals("Jane Doe", persisted.getCustomerName());
        assertEquals("test@example.com", persisted.getCustomerEmail());
        assertEquals("4242", persisted.getCardLastFour());
        assertEquals("{\"channel\":\"web\"}", persisted.getMetadata());
        assertEquals(PaymentType.CREDIT_CARD, persisted.getPaymentType());
    }

    @Test
    void processPayment_startsInPendingThenMovesToProcessing() {
        List<PaymentStatus> savedStatuses = new ArrayList<>();
        stubMerchantFound();
        stubTodaysTotal("0");
        stubNoRecentPayments();
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(1L);
            savedStatuses.add(payment.getStatus());
            return payment;
        });

        paymentService.processPayment(validRequest);

        assertEquals(PaymentStatus.PENDING, savedStatuses.get(0));
        assertEquals(PaymentStatus.PROCESSING, savedStatuses.get(1));
        assertEquals(3, savedStatuses.size());
    }

    @Test
    void processPayment_marksPaymentFailedWhenGatewayStepThrows() {
        stubMerchantFound();
        stubTodaysTotal("0");
        stubNoRecentPayments();
        AtomicInteger saveCount = new AtomicInteger();
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(1L);
            if (saveCount.incrementAndGet() == 2) {
                throw new IllegalStateException("gateway session lost");
            }
            return payment;
        });

        PaymentResponse response = paymentService.processPayment(validRequest);

        assertEquals(PaymentStatus.FAILED, response.getStatus());
        assertEquals("Processing error: gateway session lost", response.getFailureReason());
    }

    @Test
    void processPayment_returnsResponseEvenIfNotificationFails() {
        stubMerchantFound();
        stubTodaysTotal("0");
        stubNoRecentPayments();
        stubPaymentSaveEchoingArgument();
        doThrow(new RuntimeException("notification queue down"))
                .when(notificationService).sendPaymentNotification(any(Payment.class));

        PaymentResponse response = paymentService.processPayment(validRequest);

        assertNotNull(response.getTransactionId());
        verify(notificationService).sendPaymentNotification(any(Payment.class));
    }

    @Test
    void processPayment_generatesUniqueTransactionIds() {
        stubMerchantFound();
        stubTodaysTotal("0");
        stubNoRecentPayments();
        stubPaymentSaveEchoingArgument();

        String first = paymentService.processPayment(validRequest).getTransactionId();
        String second = paymentService.processPayment(validRequest).getTransactionId();

        assertEquals(20, first.length());
        assertNotEquals(first, second);
    }

    // ============================
    // Read operations
    // ============================

    @Test
    void getPaymentById_mapsEntityToResponse() {
        Payment payment = Payment.builder()
                .id(5L)
                .transactionId("TXN-READ001")
                .merchantId(1L)
                .amount(new BigDecimal("15.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.ACH)
                .description("Invoice 9")
                .customerEmail("read@example.com")
                .feeAmount(new BigDecimal("0.12"))
                .netAmount(new BigDecimal("14.88"))
                .gatewayReference("GW-12345678")
                .build();
        when(paymentRepository.findById(5L)).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentById(5L);

        assertEquals(5L, response.getId());
        assertEquals("TXN-READ001", response.getTransactionId());
        assertEquals(PaymentStatus.COMPLETED, response.getStatus());
        assertEquals(PaymentType.ACH, response.getPaymentType());
        assertEquals("Invoice 9", response.getDescription());
        assertEquals("read@example.com", response.getCustomerEmail());
        assertEquals(new BigDecimal("0.12"), response.getFeeAmount());
        assertEquals(new BigDecimal("14.88"), response.getNetAmount());
        assertEquals("GW-12345678", response.getGatewayReference());
    }

    @Test
    void getPaymentByTransactionId_returnsPayment() {
        Payment payment = Payment.builder()
                .id(6L)
                .transactionId("TXN-LOOKUP")
                .status(PaymentStatus.PENDING)
                .build();
        when(paymentRepository.findByTransactionId("TXN-LOOKUP")).thenReturn(Optional.of(payment));

        assertEquals(6L, paymentService.getPaymentByTransactionId("TXN-LOOKUP").getId());
    }

    @Test
    void getPaymentByTransactionId_notFound() {
        when(paymentRepository.findByTransactionId("TXN-MISSING")).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.getPaymentByTransactionId("TXN-MISSING"));

        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void getPaymentsByMerchant_mapsEveryPayment() {
        when(paymentRepository.findByMerchantIdAndStatus(1L, null)).thenReturn(Arrays.asList(
                Payment.builder().id(1L).transactionId("TXN-A").status(PaymentStatus.COMPLETED).build(),
                Payment.builder().id(2L).transactionId("TXN-B").status(PaymentStatus.FAILED).build()));

        List<PaymentResponse> responses = paymentService.getPaymentsByMerchant(1L);

        assertEquals(2, responses.size());
        assertEquals("TXN-A", responses.get(0).getTransactionId());
        assertEquals("TXN-B", responses.get(1).getTransactionId());
    }

    @Test
    void getPaymentsByMerchant_returnsEmptyListWhenNoPayments() {
        when(paymentRepository.findByMerchantIdAndStatus(2L, null)).thenReturn(Collections.emptyList());

        assertTrue(paymentService.getPaymentsByMerchant(2L).isEmpty());
    }

    // ============================
    // Status transitions
    // ============================

    @Test
    void updatePaymentStatus_setsCompletedAtWhenCompleting() {
        Payment payment = Payment.builder().id(1L).status(PaymentStatus.PROCESSING).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        stubPaymentSaveEchoingArgument();

        PaymentResponse response = paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED);

        assertEquals(PaymentStatus.COMPLETED, response.getStatus());
        assertNotNull(response.getCompletedAt());
    }

    @Test
    void updatePaymentStatus_doesNotSetCompletedAtForNonCompletedStatus() {
        Payment payment = Payment.builder().id(1L).status(PaymentStatus.PENDING).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        stubPaymentSaveEchoingArgument();

        PaymentResponse response = paymentService.updatePaymentStatus(1L, PaymentStatus.PROCESSING);

        assertEquals(PaymentStatus.PROCESSING, response.getStatus());
        assertNull(response.getCompletedAt());
    }

    @Test
    void updatePaymentStatus_allowsRefundOfTerminalPayment() {
        Payment payment = Payment.builder().id(1L).status(PaymentStatus.COMPLETED).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        stubPaymentSaveEchoingArgument();

        assertEquals(PaymentStatus.REFUNDED,
                paymentService.updatePaymentStatus(1L, PaymentStatus.REFUNDED).getStatus());
    }

    @Test
    void updatePaymentStatus_rejectsTransitionOutOfTerminalState() {
        Payment payment = Payment.builder().id(1L).status(PaymentStatus.FAILED).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED));

        assertEquals("INVALID_STATE_TRANSITION", exception.getErrorCode());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void updatePaymentStatus_notFound() {
        when(paymentRepository.findById(404L)).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.updatePaymentStatus(404L, PaymentStatus.COMPLETED));

        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void cancelPayment_notFound() {
        when(paymentRepository.findById(404L)).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.cancelPayment(404L));

        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void cancelPayment_recordsCancellationReason() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-CANCEL999")
                .status(PaymentStatus.PENDING)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        stubPaymentSaveEchoingArgument();

        paymentService.cancelPayment(1L);

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        assertEquals("Cancelled by user", payment.getFailureReason());
    }
}
