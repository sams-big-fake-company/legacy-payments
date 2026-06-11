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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Additional unit tests for PaymentServiceImpl covering edge cases and
 * branches not exercised by PaymentServiceImplTest.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplEdgeCasesTest {

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
    private PaymentRequest.PaymentRequestBuilder requestBuilder;

    @BeforeEach
    void setUp() {
        testMerchant = Merchant.builder()
                .id(1L)
                .merchantCode("MERCH001")
                .businessName("Test Store")
                .isActive(true)
                .dailyLimit(new BigDecimal("100000.00"))
                .build();

        requestBuilder = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("99.99"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .customerEmail("test@example.com");
    }

    private void stubHappyPathRepos() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });
    }

    @Test
    void processPayment_unsupportedCurrency_throws() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(currencyConverter.convertToUsd(any(), eq("ZWL"))).thenReturn(null);

        PaymentRequest request = requestBuilder.currency("ZWL").build();
        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("UNSUPPORTED_CURRENCY", exception.getErrorCode());
    }

    @Test
    void processPayment_foreignCurrency_convertsForLimitChecks() {
        stubHappyPathRepos();
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(currencyConverter.convertToUsd(new BigDecimal("99.99"), "EUR"))
                .thenReturn(new BigDecimal("108.74"));

        PaymentResponse response = paymentService.processPayment(requestBuilder.currency("EUR").build());

        assertNotNull(response);
        assertEquals("EUR", response.getCurrency());
        verify(currencyConverter).convertToUsd(new BigDecimal("99.99"), "EUR");
    }

    @Test
    void processPayment_wireBelowMinimum_throws() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentRequest request = requestBuilder
                .paymentType(PaymentType.WIRE)
                .amount(new BigDecimal("99.99"))
                .customerName("Alice")
                .build();

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("WIRE_MINIMUM_NOT_MET", exception.getErrorCode());
    }

    @Test
    void processPayment_wireMissingCustomerName_throws() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentRequest request = requestBuilder
                .paymentType(PaymentType.WIRE)
                .amount(new BigDecimal("500.00"))
                .customerName("   ")
                .build();

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("WIRE_NAME_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_wireValid_succeeds() {
        stubHappyPathRepos();
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(BigDecimal.ZERO);

        PaymentRequest request = requestBuilder
                .paymentType(PaymentType.WIRE)
                .amount(new BigDecimal("500.00"))
                .customerName("Alice")
                .cardLastFour(null)
                .build();

        PaymentResponse response = paymentService.processPayment(request);
        assertNotNull(response.getTransactionId());
    }

    @Test
    void processPayment_achExceedsDailyLimit_throwsInsufficientFunds() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(new BigDecimal("24990.00"));

        PaymentRequest request = requestBuilder
                .paymentType(PaymentType.ACH)
                .amount(new BigDecimal("100.00"))
                .build();

        InsufficientFundsException exception = assertThrows(InsufficientFundsException.class,
                () -> paymentService.processPayment(request));
        assertEquals("INSUFFICIENT_FUNDS", exception.getErrorCode());
        assertEquals(new BigDecimal("100.00"), exception.getRequestedAmount());
        assertEquals(new BigDecimal("10.00"), exception.getAvailableAmount());
    }

    @Test
    void processPayment_achWithinLimit_succeeds() {
        stubHappyPathRepos();
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(BigDecimal.ZERO);

        PaymentRequest request = requestBuilder
                .paymentType(PaymentType.ACH)
                .cardLastFour(null)
                .build();

        PaymentResponse response = paymentService.processPayment(request);
        assertNotNull(response.getTransactionId());
    }

    @Test
    void processPayment_creditCardMissingLastFour_throws() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentRequest request = requestBuilder.cardLastFour(null).build();
        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("CARD_INFO_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_creditCardVelocityExceeded_throws() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        List<Payment> tenRecentPayments = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tenRecentPayments.add(Payment.builder().id((long) i).build());
        }
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(tenRecentPayments);

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(requestBuilder.build()));
        assertEquals("VELOCITY_EXCEEDED", exception.getErrorCode());
    }

    @Test
    void processPayment_debitMissingLastFour_throws() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentRequest request = requestBuilder
                .paymentType(PaymentType.DEBIT)
                .cardLastFour("42")
                .build();

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("CARD_INFO_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_merchantDailyLimitExceeded_throwsInsufficientFunds() {
        testMerchant.setDailyLimit(new BigDecimal("1000.00"));
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(new BigDecimal("950.00"));
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());

        InsufficientFundsException exception = assertThrows(InsufficientFundsException.class,
                () -> paymentService.processPayment(requestBuilder.build()));
        assertEquals(new BigDecimal("50.00"), exception.getAvailableAmount());
    }

    @Test
    void processPayment_merchantWithoutDailyLimit_skipsLimitCheck() {
        testMerchant.setDailyLimit(null);
        stubHappyPathRepos();
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());

        PaymentResponse response = paymentService.processPayment(requestBuilder.build());

        assertNotNull(response.getTransactionId());
        verify(paymentRepository, never()).sumCompletedAmountByMerchantSince(anyLong(), any());
    }

    @Test
    void processPayment_calculatesCreditCardFeeAndNetAmount() {
        stubHappyPathRepos();
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());

        PaymentRequest request = requestBuilder.amount(new BigDecimal("100.00")).build();
        PaymentResponse response = paymentService.processPayment(request);

        assertEquals(new BigDecimal("2.9000"), response.getFeeAmount());
        assertEquals(new BigDecimal("97.1000"), response.getNetAmount());
    }

    @Test
    void processPayment_notificationFailure_doesNotFailPayment() {
        stubHappyPathRepos();
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
        doThrow(new RuntimeException("smtp down"))
                .when(notificationService).sendPaymentNotification(any(Payment.class));

        PaymentResponse response = paymentService.processPayment(requestBuilder.build());

        assertNotNull(response.getTransactionId());
        verify(notificationService).sendPaymentNotification(any(Payment.class));
    }

    @Test
    void processPayment_blankIdempotencyKey_skipsLookup() {
        stubHappyPathRepos();
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());

        paymentService.processPayment(requestBuilder.idempotencyKey("").build());

        verify(paymentRepository, never()).findByIdempotencyKey(any());
    }

    @Test
    void processPayment_newIdempotencyKey_storesKeyOnPayment() {
        stubHappyPathRepos();
        when(paymentRepository.findByIdempotencyKey("fresh-key")).thenReturn(Optional.empty());
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());

        paymentService.processPayment(requestBuilder.idempotencyKey("fresh-key").build());

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, atLeastOnce()).save(captor.capture());
        assertEquals("fresh-key", captor.getAllValues().get(0).getIdempotencyKey());
    }

    @Test
    void getPaymentById_found_mapsAllFields() {
        Payment payment = Payment.builder()
                .id(7L)
                .transactionId("TXN-FOUND")
                .merchantId(2L)
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.ACH)
                .feeAmount(new BigDecimal("0.08"))
                .netAmount(new BigDecimal("9.92"))
                .gatewayReference("GW-REF")
                .completedAt(LocalDateTime.of(2024, 1, 1, 12, 0))
                .build();
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentById(7L);

        assertEquals(7L, response.getId());
        assertEquals("TXN-FOUND", response.getTransactionId());
        assertEquals(2L, response.getMerchantId());
        assertEquals(PaymentStatus.COMPLETED, response.getStatus());
        assertEquals(PaymentType.ACH, response.getPaymentType());
        assertEquals("GW-REF", response.getGatewayReference());
        assertEquals(LocalDateTime.of(2024, 1, 1, 12, 0), response.getCompletedAt());
    }

    @Test
    void getPaymentByTransactionId_found_returnsResponse() {
        Payment payment = Payment.builder()
                .id(8L)
                .transactionId("TXN-BYTXN")
                .status(PaymentStatus.PENDING)
                .build();
        when(paymentRepository.findByTransactionId("TXN-BYTXN")).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentByTransactionId("TXN-BYTXN");
        assertEquals("TXN-BYTXN", response.getTransactionId());
    }

    @Test
    void getPaymentByTransactionId_notFound_throws() {
        when(paymentRepository.findByTransactionId("TXN-MISSING")).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.getPaymentByTransactionId("TXN-MISSING"));
        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void getPaymentsByMerchant_mapsAllPayments() {
        Payment p1 = Payment.builder().id(1L).transactionId("TXN-1").status(PaymentStatus.COMPLETED).build();
        Payment p2 = Payment.builder().id(2L).transactionId("TXN-2").status(PaymentStatus.FAILED).build();
        when(paymentRepository.findByMerchantIdAndStatus(5L, null)).thenReturn(Arrays.asList(p1, p2));

        List<PaymentResponse> responses = paymentService.getPaymentsByMerchant(5L);

        assertEquals(2, responses.size());
        assertEquals("TXN-1", responses.get(0).getTransactionId());
        assertEquals("TXN-2", responses.get(1).getTransactionId());
    }

    @Test
    void updatePaymentStatus_pendingToProcessing_succeeds() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-UPD")
                .status(PaymentStatus.PENDING)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = paymentService.updatePaymentStatus(1L, PaymentStatus.PROCESSING);

        assertEquals(PaymentStatus.PROCESSING, response.getStatus());
        assertNull(response.getCompletedAt());
    }

    @Test
    void updatePaymentStatus_toCompleted_setsCompletedAt() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-UPD2")
                .status(PaymentStatus.PROCESSING)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED);

        assertEquals(PaymentStatus.COMPLETED, response.getStatus());
        assertNotNull(response.getCompletedAt());
    }

    @Test
    void updatePaymentStatus_terminalToNonRefunded_throws() {
        Payment payment = Payment.builder()
                .id(1L)
                .status(PaymentStatus.FAILED)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.updatePaymentStatus(1L, PaymentStatus.PENDING));
        assertEquals("INVALID_STATE_TRANSITION", exception.getErrorCode());
    }

    @Test
    void updatePaymentStatus_completedToRefunded_isAllowed() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-REF")
                .status(PaymentStatus.COMPLETED)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = paymentService.updatePaymentStatus(1L, PaymentStatus.REFUNDED);
        assertEquals(PaymentStatus.REFUNDED, response.getStatus());
    }

    @Test
    void updatePaymentStatus_notFound_throws() {
        when(paymentRepository.findById(99L)).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.updatePaymentStatus(99L, PaymentStatus.COMPLETED));
        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void cancelPayment_notFound_throws() {
        when(paymentRepository.findById(99L)).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.cancelPayment(99L));
        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void cancelPayment_setsFailureReason() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-CXL")
                .status(PaymentStatus.PENDING)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentService.cancelPayment(1L);

        verify(paymentRepository).save(argThat(p ->
                p.getStatus() == PaymentStatus.FAILED && "Cancelled by user".equals(p.getFailureReason())));
    }
}
