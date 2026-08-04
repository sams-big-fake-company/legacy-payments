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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests covering the payment-type, currency and limit rules in PaymentServiceImpl
 * that the original PaymentServiceImplTest did not exercise.
 *
 * The simulated gateway in PaymentServiceImpl is non-deterministic (95% success rate),
 * so assertions on successful flows only cover behaviour that is identical for both
 * gateway outcomes (persisted request data, fees, terminal status).
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplBusinessRulesTest {

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

    private static PaymentRequest.PaymentRequestBuilder request() {
        return PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("500.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .customerEmail("buyer@example.com");
    }

    private void merchantFound() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
    }

    private void noTransactionsToday() {
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any())).thenReturn(null);
    }

    private void noRecentTransactions() {
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
    }

    private void saveEchoesEntity() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Payment lastSavedPayment() {
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    // ---------------------------------------------------------------
    // Wire transfers
    // ---------------------------------------------------------------

    @Test
    void processPayment_wireBelowMinimumIsRejected() {
        merchantFound();

        PaymentException exception = assertThrows(PaymentException.class, () -> paymentService.processPayment(
                request().paymentType(PaymentType.WIRE).amount(new BigDecimal("99.99")).customerName("Ada").build()));

        assertEquals("WIRE_MINIMUM_NOT_MET", exception.getErrorCode());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void processPayment_wireWithoutCustomerNameIsRejected() {
        merchantFound();

        PaymentException exception = assertThrows(PaymentException.class, () -> paymentService.processPayment(
                request().paymentType(PaymentType.WIRE).customerName("   ").build()));

        assertEquals("WIRE_NAME_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_wireUsesConvertedAmountForTheMinimumCheck() {
        merchantFound();
        // 90 GBP converts to 113.85 USD, which clears the $100 wire minimum.
        when(currencyConverter.convertToUsd(new BigDecimal("90.00"), "GBP")).thenReturn(new BigDecimal("113.8500"));
        noTransactionsToday();
        saveEchoesEntity();

        PaymentResponse response = paymentService.processPayment(request()
                .paymentType(PaymentType.WIRE)
                .amount(new BigDecimal("90.00"))
                .currency("GBP")
                .customerName("Ada Lovelace")
                .build());

        assertEquals("GBP", response.getCurrency());
        assertEquals(new BigDecimal("90.00"), response.getAmount());
    }

    @Test
    void processPayment_wireAtExactlyTheMinimumIsAccepted() {
        merchantFound();
        noTransactionsToday();
        saveEchoesEntity();

        PaymentResponse response = paymentService.processPayment(request()
                .paymentType(PaymentType.WIRE)
                .amount(new BigDecimal("100.00"))
                .customerName("Ada Lovelace")
                .build());

        assertNotNull(response.getTransactionId());
    }

    // ---------------------------------------------------------------
    // ACH
    // ---------------------------------------------------------------

    @Test
    void processPayment_achOverDailyLimitReportsRemainingHeadroom() {
        merchantFound();
        when(paymentRepository.sumCompletedAmountByMerchantSince(eq(1L), any()))
                .thenReturn(new BigDecimal("24000.00"));

        InsufficientFundsException exception = assertThrows(InsufficientFundsException.class,
                () -> paymentService.processPayment(request()
                        .paymentType(PaymentType.ACH)
                        .amount(new BigDecimal("2000.00"))
                        .build()));

        assertEquals("INSUFFICIENT_FUNDS", exception.getErrorCode());
        assertEquals(new BigDecimal("2000.00"), exception.getRequestedAmount());
        assertEquals(new BigDecimal("1000.00"), exception.getAvailableAmount());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void processPayment_achWithinDailyLimitIsAccepted() {
        merchantFound();
        when(paymentRepository.sumCompletedAmountByMerchantSince(eq(1L), any()))
                .thenReturn(new BigDecimal("20000.00"));
        saveEchoesEntity();

        PaymentResponse response = paymentService.processPayment(request()
                .paymentType(PaymentType.ACH)
                .amount(new BigDecimal("5000.00"))
                .build());

        assertNotNull(response.getTransactionId());
    }

    // ---------------------------------------------------------------
    // Card payments
    // ---------------------------------------------------------------

    @ParameterizedTest
    @CsvSource(value = {
            "CREDIT_CARD,NULL",
            "CREDIT_CARD,123",
            "CREDIT_CARD,12345",
            "DEBIT,NULL",
            "DEBIT,123"
    }, nullValues = "NULL")
    void processPayment_cardPaymentsRequireExactlyFourCardDigits(PaymentType type, String cardLastFour) {
        merchantFound();

        PaymentException exception = assertThrows(PaymentException.class, () -> paymentService.processPayment(
                request().paymentType(type).cardLastFour(cardLastFour).build()));

        assertEquals("CARD_INFO_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_creditCardVelocityLimitIsEnforced() {
        merchantFound();
        List<Payment> recent = IntStream.range(0, 10)
                .mapToObj(i -> Payment.builder().id((long) i).build())
                .collect(Collectors.toList());
        when(paymentRepository.findByMerchantIdAndDateRange(eq(1L), any(), any())).thenReturn(recent);

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request().build()));

        assertEquals("VELOCITY_EXCEEDED", exception.getErrorCode());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void processPayment_creditCardJustBelowVelocityLimitIsAccepted() {
        merchantFound();
        List<Payment> recent = IntStream.range(0, 9)
                .mapToObj(i -> Payment.builder().id((long) i).build())
                .collect(Collectors.toList());
        when(paymentRepository.findByMerchantIdAndDateRange(eq(1L), any(), any())).thenReturn(recent);
        noTransactionsToday();
        saveEchoesEntity();

        assertNotNull(paymentService.processPayment(request().build()).getTransactionId());
    }

    @Test
    void processPayment_debitPaymentSkipsTheVelocityCheck() {
        merchantFound();
        noTransactionsToday();
        saveEchoesEntity();

        paymentService.processPayment(request().paymentType(PaymentType.DEBIT).build());

        verify(paymentRepository, never()).findByMerchantIdAndDateRange(anyLong(), any(), any());
    }

    // ---------------------------------------------------------------
    // Currency handling
    // ---------------------------------------------------------------

    @Test
    void processPayment_unsupportedCurrencyIsRejected() {
        merchantFound();
        when(currencyConverter.convertToUsd(any(), eq("XYZ"))).thenReturn(null);

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request().currency("XYZ").build()));

        assertEquals("UNSUPPORTED_CURRENCY", exception.getErrorCode());
    }

    @Test
    void processPayment_usdRequestsSkipCurrencyConversion() {
        merchantFound();
        noRecentTransactions();
        noTransactionsToday();
        saveEchoesEntity();

        paymentService.processPayment(request().build());

        verifyNoInteractions(currencyConverter);
    }

    @Test
    void processPayment_nullCurrencyIsTreatedAsUsd() {
        merchantFound();
        noRecentTransactions();
        noTransactionsToday();
        saveEchoesEntity();

        paymentService.processPayment(request().currency(null).build());

        verifyNoInteractions(currencyConverter);
    }

    @Test
    void processPayment_merchantLimitIsCheckedAgainstTheConvertedAmount() {
        merchant.setDailyLimit(new BigDecimal("1000.00"));
        merchantFound();
        // 900 EUR is 978.75 USD, which fits under the $1000 daily limit.
        when(currencyConverter.convertToUsd(new BigDecimal("900.00"), "EUR")).thenReturn(new BigDecimal("978.7500"));
        noRecentTransactions();
        noTransactionsToday();
        saveEchoesEntity();

        PaymentResponse response = paymentService.processPayment(request()
                .amount(new BigDecimal("900.00"))
                .currency("EUR")
                .build());

        // The stored amount stays in the original currency.
        assertEquals(new BigDecimal("900.00"), response.getAmount());
        assertEquals("EUR", response.getCurrency());
    }

    // ---------------------------------------------------------------
    // Merchant daily limit
    // ---------------------------------------------------------------

    @Test
    void processPayment_merchantDailyLimitExceeded() {
        merchant.setDailyLimit(new BigDecimal("1000.00"));
        merchantFound();
        noRecentTransactions();
        when(paymentRepository.sumCompletedAmountByMerchantSince(eq(1L), any()))
                .thenReturn(new BigDecimal("800.00"));

        InsufficientFundsException exception = assertThrows(InsufficientFundsException.class,
                () -> paymentService.processPayment(request().amount(new BigDecimal("500.00")).build()));

        assertEquals(new BigDecimal("500.00"), exception.getRequestedAmount());
        assertEquals(new BigDecimal("200.00"), exception.getAvailableAmount());
    }

    @Test
    void processPayment_merchantWithoutDailyLimitSkipsTheLimitQuery() {
        merchant.setDailyLimit(null);
        merchantFound();
        noRecentTransactions();
        saveEchoesEntity();

        paymentService.processPayment(request().build());

        verify(paymentRepository, never()).sumCompletedAmountByMerchantSince(anyLong(), any());
    }

    // ---------------------------------------------------------------
    // Fees, persistence and notifications
    // ---------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
            "CREDIT_CARD, 100.00, 2.9000, 97.1000",
            "DEBIT, 100.00, 1.5000, 98.5000",
            "WIRE, 100.00, 0.1000, 99.9000",
            "ACH, 100.00, 0.8000, 99.2000",
            "CREDIT_CARD, 99.99, 2.8997, 97.0903"
    })
    void processPayment_feesAreDerivedFromThePaymentType(
            PaymentType type, String amount, String expectedFee, String expectedNet) {
        merchantFound();
        noTransactionsToday();
        saveEchoesEntity();

        paymentService.processPayment(request()
                .paymentType(type)
                .amount(new BigDecimal(amount))
                .customerName("Ada Lovelace")
                .build());

        Payment saved = lastSavedPayment();
        assertEquals(new BigDecimal(expectedFee), saved.getFeeAmount());
        assertEquals(new BigDecimal(expectedNet), saved.getNetAmount());
    }

    @Test
    void processPayment_persistsRequestDetailsAndReachesATerminalStatus() {
        merchantFound();
        noRecentTransactions();
        noTransactionsToday();
        saveEchoesEntity();

        PaymentResponse response = paymentService.processPayment(request()
                .description("Order 42")
                .customerName("Ada Lovelace")
                .idempotencyKey("idem-1")
                .metadata("{\"source\":\"web\"}")
                .build());

        Payment saved = lastSavedPayment();
        assertEquals(1L, saved.getMerchantId());
        assertEquals("Order 42", saved.getDescription());
        assertEquals("buyer@example.com", saved.getCustomerEmail());
        assertEquals("Ada Lovelace", saved.getCustomerName());
        assertEquals("4242", saved.getCardLastFour());
        assertEquals("idem-1", saved.getIdempotencyKey());
        assertEquals("{\"source\":\"web\"}", saved.getMetadata());
        assertTrue(saved.getStatus().isTerminal(), "gateway call must leave the payment in a terminal state");
        assertEquals(saved.getStatus(), response.getStatus());
        if (saved.getStatus() == PaymentStatus.COMPLETED) {
            assertNotNull(saved.getCompletedAt());
            assertTrue(saved.getGatewayReference().startsWith("GW-"));
        } else {
            assertEquals("Gateway declined the transaction", saved.getFailureReason());
        }
    }

    @Test
    void processPayment_lookupIsSkippedForAnEmptyIdempotencyKey() {
        merchantFound();
        noRecentTransactions();
        noTransactionsToday();
        saveEchoesEntity();

        paymentService.processPayment(request().idempotencyKey("").build());

        verify(paymentRepository, never()).findByIdempotencyKey(any());
    }

    @Test
    void processPayment_unknownIdempotencyKeyProcessesANewPayment() {
        when(paymentRepository.findByIdempotencyKey("idem-new")).thenReturn(Optional.empty());
        merchantFound();
        noRecentTransactions();
        noTransactionsToday();
        saveEchoesEntity();

        PaymentResponse response = paymentService.processPayment(request().idempotencyKey("idem-new").build());

        assertTrue(response.getTransactionId().startsWith("TXN-"));
    }

    @Test
    void processPayment_notificationFailureDoesNotFailThePayment() {
        merchantFound();
        noRecentTransactions();
        noTransactionsToday();
        saveEchoesEntity();
        doThrow(new IllegalStateException("webhook down"))
                .when(notificationService).sendPaymentNotification(any(Payment.class));

        PaymentResponse response = paymentService.processPayment(request().build());

        assertNotNull(response.getTransactionId());
        verify(notificationService).sendPaymentNotification(any(Payment.class));
    }

    @Test
    void processPayment_generatesUniquePrefixedTransactionIds() {
        merchantFound();
        noRecentTransactions();
        noTransactionsToday();
        saveEchoesEntity();

        String first = paymentService.processPayment(request().build()).getTransactionId();
        String second = paymentService.processPayment(request().build()).getTransactionId();

        assertTrue(first.matches("TXN-[0-9A-F]{16}"), "unexpected transaction id: " + first);
        assertNotEquals(first, second);
    }

    @Test
    void processPayment_gatewayFailureIsRecordedAsAFailedPayment() {
        merchantFound();
        noRecentTransactions();
        noTransactionsToday();
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0))
                .thenThrow(new IllegalStateException("gateway exploded"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentService.processPayment(request().build());

        assertEquals(PaymentStatus.FAILED, response.getStatus());
        assertEquals("Processing error: gateway exploded", response.getFailureReason());
    }

    // ---------------------------------------------------------------
    // Reads and status transitions
    // ---------------------------------------------------------------

    @Test
    void getPaymentById_mapsEntityToResponse() {
        Payment payment = Payment.builder()
                .id(7L)
                .transactionId("TXN-READ7")
                .merchantId(1L)
                .amount(new BigDecimal("12.34"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.DEBIT)
                .feeAmount(new BigDecimal("0.1851"))
                .netAmount(new BigDecimal("12.1549"))
                .gatewayReference("GW-1234abcd")
                .createdAt(LocalDateTime.of(2024, 1, 1, 10, 0))
                .completedAt(LocalDateTime.of(2024, 1, 1, 10, 1))
                .build();
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentById(7L);

        assertEquals(7L, response.getId());
        assertEquals("TXN-READ7", response.getTransactionId());
        assertEquals(PaymentType.DEBIT, response.getPaymentType());
        assertEquals(new BigDecimal("0.1851"), response.getFeeAmount());
        assertEquals(new BigDecimal("12.1549"), response.getNetAmount());
        assertEquals("GW-1234abcd", response.getGatewayReference());
        assertEquals(LocalDateTime.of(2024, 1, 1, 10, 1), response.getCompletedAt());
    }

    @Test
    void getPaymentByTransactionId_returnsThePayment() {
        Payment payment = Payment.builder().id(3L).transactionId("TXN-ABC").status(PaymentStatus.PENDING).build();
        when(paymentRepository.findByTransactionId("TXN-ABC")).thenReturn(Optional.of(payment));

        assertEquals(3L, paymentService.getPaymentByTransactionId("TXN-ABC").getId());
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
        List<Payment> payments = Arrays.asList(
                Payment.builder().id(1L).transactionId("TXN-1").status(PaymentStatus.COMPLETED).build(),
                Payment.builder().id(2L).transactionId("TXN-2").status(PaymentStatus.FAILED).build());
        when(paymentRepository.findByMerchantIdAndStatus(1L, null)).thenReturn(payments);

        List<PaymentResponse> responses = paymentService.getPaymentsByMerchant(1L);

        assertEquals(Arrays.asList("TXN-1", "TXN-2"),
                responses.stream().map(PaymentResponse::getTransactionId).collect(Collectors.toList()));
    }

    @Test
    void getPaymentsByMerchant_returnsEmptyListWhenMerchantHasNoPayments() {
        when(paymentRepository.findByMerchantIdAndStatus(9L, null)).thenReturn(Collections.emptyList());

        assertEquals(Collections.emptyList(), paymentService.getPaymentsByMerchant(9L));
    }

    @Test
    void updatePaymentStatus_completedSetsCompletedAt() {
        Payment payment = Payment.builder().id(1L).transactionId("TXN-1").status(PaymentStatus.PROCESSING).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        saveEchoesEntity();

        PaymentResponse response = paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED);

        assertEquals(PaymentStatus.COMPLETED, response.getStatus());
        assertNotNull(response.getCompletedAt());
    }

    @Test
    void updatePaymentStatus_failedDoesNotSetCompletedAt() {
        Payment payment = Payment.builder().id(1L).transactionId("TXN-1").status(PaymentStatus.PROCESSING).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        saveEchoesEntity();

        PaymentResponse response = paymentService.updatePaymentStatus(1L, PaymentStatus.FAILED);

        assertEquals(PaymentStatus.FAILED, response.getStatus());
        assertNull(response.getCompletedAt());
    }

    @Test
    void updatePaymentStatus_completedPaymentCanStillBeRefunded() {
        Payment payment = Payment.builder().id(1L).transactionId("TXN-1").status(PaymentStatus.COMPLETED).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        saveEchoesEntity();

        assertEquals(PaymentStatus.REFUNDED,
                paymentService.updatePaymentStatus(1L, PaymentStatus.REFUNDED).getStatus());
    }

    @ParameterizedTest
    @CsvSource({"COMPLETED, FAILED", "FAILED, COMPLETED", "REFUNDED, PENDING"})
    void updatePaymentStatus_terminalStatesCannotTransition(PaymentStatus from, PaymentStatus to) {
        Payment payment = Payment.builder().id(1L).transactionId("TXN-1").status(from).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.updatePaymentStatus(1L, to));

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

        PaymentException exception = assertThrows(PaymentException.class, () -> paymentService.cancelPayment(404L));

        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void cancelPayment_recordsTheCancellationReason() {
        Payment payment = Payment.builder().id(1L).transactionId("TXN-1").status(PaymentStatus.PENDING).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        saveEchoesEntity();

        paymentService.cancelPayment(1L);

        assertEquals("Cancelled by user", lastSavedPayment().getFailureReason());
    }
}
