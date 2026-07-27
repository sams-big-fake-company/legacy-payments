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
    // Additional edge case coverage (PAY-3455)
    // ============================

    /** Stubs the repository so a payment survives the save round-trips in processPayment. */
    private void stubSaveEchoingPayment() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });
    }

    private void stubNoPriorActivity() {
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any())).thenReturn(BigDecimal.ZERO);
    }

    private void stubNoRecentPayments() {
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(java.util.Collections.emptyList());
    }

    @Test
    void processPayment_emptyIdempotencyKey_isNotTreatedAsIdempotent() {
        validRequest.setIdempotencyKey("");
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        stubNoPriorActivity();
        stubNoRecentPayments();
        stubSaveEchoingPayment();

        PaymentResponse response = paymentService.processPayment(validRequest);

        assertNotNull(response.getTransactionId());
        verify(paymentRepository, never()).findByIdempotencyKey(any());
    }

    @Test
    void processPayment_creditCard_calculatesFeeAndNetAmount() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        stubNoPriorActivity();
        stubNoRecentPayments();
        stubSaveEchoingPayment();

        PaymentResponse response = paymentService.processPayment(validRequest);

        // 99.99 * 2.9% = 2.899710
        assertEquals(new BigDecimal("2.8997"), response.getFeeAmount());
        assertEquals(new BigDecimal("97.0903"), response.getNetAmount());
    }

    @Test
    void processPayment_creditCardWithoutCardLastFour_throwsCardInfoRequired() {
        validRequest.setCardLastFour(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));
        assertEquals("CARD_INFO_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_creditCardVelocityExceeded_throwsVelocityExceeded() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(java.util.Collections.nCopies(10, Payment.builder().id(1L).build()));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));
        assertEquals("VELOCITY_EXCEEDED", exception.getErrorCode());
    }

    @Test
    void processPayment_debitWithoutCardLastFour_throwsCardInfoRequired() {
        validRequest.setPaymentType(PaymentType.DEBIT);
        validRequest.setCardLastFour("12");
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));
        assertEquals("CARD_INFO_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_debit_usesDebitFeeRate() {
        validRequest.setPaymentType(PaymentType.DEBIT);
        validRequest.setAmount(new BigDecimal("200.00"));
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        stubNoPriorActivity();
        stubSaveEchoingPayment();

        PaymentResponse response = paymentService.processPayment(validRequest);

        assertEquals(new BigDecimal("3.0000"), response.getFeeAmount());
    }

    @Test
    void processPayment_wireBelowMinimum_throwsWireMinimumNotMet() {
        validRequest.setPaymentType(PaymentType.WIRE);
        validRequest.setAmount(new BigDecimal("99.99"));
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));
        assertEquals("WIRE_MINIMUM_NOT_MET", exception.getErrorCode());
    }

    @Test
    void processPayment_wireWithoutCustomerName_throwsWireNameRequired() {
        validRequest.setPaymentType(PaymentType.WIRE);
        validRequest.setAmount(new BigDecimal("500.00"));
        validRequest.setCustomerName("   ");
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));
        assertEquals("WIRE_NAME_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_validWire_isAccepted() {
        validRequest.setPaymentType(PaymentType.WIRE);
        validRequest.setAmount(new BigDecimal("500.00"));
        validRequest.setCustomerName("Acme Corp");
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        stubNoPriorActivity();
        stubSaveEchoingPayment();

        PaymentResponse response = paymentService.processPayment(validRequest);

        assertEquals(new BigDecimal("0.5000"), response.getFeeAmount());
    }

    @Test
    void processPayment_achWithinDailyLimit_isAccepted() {
        validRequest.setPaymentType(PaymentType.ACH);
        validRequest.setAmount(new BigDecimal("1000.00"));
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(new BigDecimal("500.00"));
        stubSaveEchoingPayment();

        PaymentResponse response = paymentService.processPayment(validRequest);

        assertEquals(new BigDecimal("8.0000"), response.getFeeAmount());
    }

    @Test
    void processPayment_achAboveDailyLimit_throwsInsufficientFunds() {
        validRequest.setPaymentType(PaymentType.ACH);
        validRequest.setAmount(new BigDecimal("5000.00"));
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(new BigDecimal("24000.00"));

        InsufficientFundsException exception = assertThrows(InsufficientFundsException.class,
                () -> paymentService.processPayment(validRequest));
        assertEquals("INSUFFICIENT_FUNDS", exception.getErrorCode());
        assertEquals(new BigDecimal("5000.00"), exception.getRequestedAmount());
        assertEquals(new BigDecimal("1000.00"), exception.getAvailableAmount());
    }

    @Test
    void processPayment_nullTotalFromRepository_isTreatedAsZero() {
        validRequest.setPaymentType(PaymentType.ACH);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any())).thenReturn(null);
        stubSaveEchoingPayment();

        PaymentResponse response = paymentService.processPayment(validRequest);

        assertNotNull(response.getTransactionId());
    }

    @Test
    void processPayment_merchantDailyLimitExceeded_throwsInsufficientFunds() {
        testMerchant.setDailyLimit(new BigDecimal("100.00"));
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(new BigDecimal("50.00"));
        stubNoRecentPayments();

        InsufficientFundsException exception = assertThrows(InsufficientFundsException.class,
                () -> paymentService.processPayment(validRequest));
        assertEquals(new BigDecimal("50.00"), exception.getAvailableAmount());
    }

    @Test
    void processPayment_merchantWithoutDailyLimit_skipsLimitCheck() {
        testMerchant.setDailyLimit(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        stubNoRecentPayments();
        stubSaveEchoingPayment();

        PaymentResponse response = paymentService.processPayment(validRequest);

        assertNotNull(response.getTransactionId());
        verify(paymentRepository, never()).sumCompletedAmountByMerchantSince(anyLong(), any());
    }

    @Test
    void processPayment_nonUsdCurrency_convertsBeforeLimitChecks() {
        validRequest.setCurrency("EUR");
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(currencyConverter.convertToUsd(new BigDecimal("99.99"), "EUR"))
                .thenReturn(new BigDecimal("108.7391"));
        stubNoPriorActivity();
        stubNoRecentPayments();
        stubSaveEchoingPayment();

        PaymentResponse response = paymentService.processPayment(validRequest);

        assertEquals("EUR", response.getCurrency());
        verify(currencyConverter).convertToUsd(new BigDecimal("99.99"), "EUR");
    }

    @Test
    void processPayment_unsupportedCurrency_throwsUnsupportedCurrency() {
        validRequest.setCurrency("ZWL");
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(currencyConverter.convertToUsd(any(), eq("ZWL"))).thenReturn(null);

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));
        assertEquals("UNSUPPORTED_CURRENCY", exception.getErrorCode());
    }

    @Test
    void processPayment_notificationFailure_doesNotFailPayment() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        stubNoPriorActivity();
        stubNoRecentPayments();
        stubSaveEchoingPayment();
        doThrow(new RuntimeException("notification service down"))
                .when(notificationService).sendPaymentNotification(any(Payment.class));

        PaymentResponse response = paymentService.processPayment(validRequest);

        assertNotNull(response.getTransactionId());
        verify(notificationService).sendPaymentNotification(any(Payment.class));
    }

    @Test
    void getPaymentById_found_mapsToResponse() {
        Payment payment = Payment.builder()
                .id(3L)
                .transactionId("TXN-FOUND3")
                .merchantId(1L)
                .amount(new BigDecimal("12.34"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.ACH)
                .gatewayReference("GW-1234")
                .build();
        when(paymentRepository.findById(3L)).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentById(3L);

        assertEquals(3L, response.getId());
        assertEquals("TXN-FOUND3", response.getTransactionId());
        assertEquals("GW-1234", response.getGatewayReference());
        assertEquals(PaymentStatus.COMPLETED, response.getStatus());
    }

    @Test
    void getPaymentByTransactionId_found_mapsToResponse() {
        Payment payment = Payment.builder()
                .id(4L)
                .transactionId("TXN-BYTXN")
                .amount(new BigDecimal("1.00"))
                .status(PaymentStatus.PENDING)
                .paymentType(PaymentType.WIRE)
                .build();
        when(paymentRepository.findByTransactionId("TXN-BYTXN")).thenReturn(Optional.of(payment));

        assertEquals(4L, paymentService.getPaymentByTransactionId("TXN-BYTXN").getId());
    }

    @Test
    void getPaymentByTransactionId_notFound_throwsPaymentNotFound() {
        when(paymentRepository.findByTransactionId("TXN-NOPE")).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.getPaymentByTransactionId("TXN-NOPE"));
        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void getPaymentsByMerchant_mapsEveryPayment() {
        when(paymentRepository.findByMerchantIdAndStatus(1L, null)).thenReturn(java.util.Arrays.asList(
                Payment.builder().id(1L).transactionId("TXN-A").status(PaymentStatus.COMPLETED).build(),
                Payment.builder().id(2L).transactionId("TXN-B").status(PaymentStatus.FAILED).build()));

        java.util.List<PaymentResponse> responses = paymentService.getPaymentsByMerchant(1L);

        assertEquals(2, responses.size());
        assertEquals("TXN-A", responses.get(0).getTransactionId());
        assertEquals("TXN-B", responses.get(1).getTransactionId());
    }

    @Test
    void getPaymentsByMerchant_noPayments_returnsEmptyList() {
        when(paymentRepository.findByMerchantIdAndStatus(2L, null)).thenReturn(java.util.Collections.emptyList());

        assertTrue(paymentService.getPaymentsByMerchant(2L).isEmpty());
    }

    @Test
    void updatePaymentStatus_toCompleted_setsCompletedAt() {
        Payment payment = Payment.builder().id(1L).transactionId("TXN-UPD").status(PaymentStatus.PROCESSING).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        PaymentResponse response = paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED);

        assertEquals(PaymentStatus.COMPLETED, response.getStatus());
        assertNotNull(response.getCompletedAt());
    }

    @Test
    void updatePaymentStatus_toFailed_leavesCompletedAtUnset() {
        Payment payment = Payment.builder().id(1L).transactionId("TXN-UPD").status(PaymentStatus.PROCESSING).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        PaymentResponse response = paymentService.updatePaymentStatus(1L, PaymentStatus.FAILED);

        assertEquals(PaymentStatus.FAILED, response.getStatus());
        assertNull(response.getCompletedAt());
    }

    @Test
    void updatePaymentStatus_completedToRefunded_isAllowed() {
        Payment payment = Payment.builder().id(1L).transactionId("TXN-REF").status(PaymentStatus.COMPLETED).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        PaymentResponse response = paymentService.updatePaymentStatus(1L, PaymentStatus.REFUNDED);

        assertEquals(PaymentStatus.REFUNDED, response.getStatus());
    }

    @Test
    void updatePaymentStatus_fromTerminalState_throwsInvalidStateTransition() {
        Payment payment = Payment.builder().id(1L).status(PaymentStatus.FAILED).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED));
        assertEquals("INVALID_STATE_TRANSITION", exception.getErrorCode());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void updatePaymentStatus_paymentNotFound_throwsPaymentNotFound() {
        when(paymentRepository.findById(42L)).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.updatePaymentStatus(42L, PaymentStatus.COMPLETED));
        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void cancelPayment_paymentNotFound_throwsPaymentNotFound() {
        when(paymentRepository.findById(42L)).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.cancelPayment(42L));
        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void cancelPayment_setsFailureReason() {
        Payment payment = Payment.builder().id(1L).transactionId("TXN-CANCEL").status(PaymentStatus.PENDING).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        paymentService.cancelPayment(1L);

        assertEquals("Cancelled by user", payment.getFailureReason());
    }
}
