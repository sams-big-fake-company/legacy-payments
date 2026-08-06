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
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Additional branch coverage for PaymentServiceImpl: payment-type specific rules,
 * limit checks, currency conversion, status transitions and lookups.
 *
 * Note: the simulated gateway in PaymentServiceImpl fails randomly ~5% of the time
 * (PAY-3220), so these tests never assert on a COMPLETED outcome - they assert on
 * the deterministic behaviour that happens before or independently of the gateway call.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplBranchTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private PaymentValidator paymentValidator;

    @Spy
    private CurrencyConverter currencyConverter = new CurrencyConverter();

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
                .amount(new BigDecimal("99.99"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242");
    }

    private void stubMerchantFound() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
    }

    private void stubDailyTotal(BigDecimal total) {
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any())).thenReturn(total);
    }

    private void stubNoRecentPayments() {
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
    }

    private void stubSaveEchoesEntity() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(1L);
            return payment;
        });
    }

    private Payment firstSavedPayment() {
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues().get(0);
    }

    // ---------------------------------------------------------------
    // Idempotency
    // ---------------------------------------------------------------

    @Test
    void processPayment_emptyIdempotencyKeySkipsLookupAndProcessesNormally() {
        stubMerchantFound();
        stubDailyTotal(BigDecimal.ZERO);
        stubNoRecentPayments();
        stubSaveEchoesEntity();

        PaymentResponse response = paymentService.processPayment(request().idempotencyKey("").build());

        assertNotNull(response.getTransactionId());
        verify(paymentRepository, org.mockito.Mockito.never()).findByIdempotencyKey(any());
    }

    // ---------------------------------------------------------------
    // Currency handling
    // ---------------------------------------------------------------

    @Test
    void processPayment_rejectsUnsupportedCurrency() {
        stubMerchantFound();
        // CurrencyConverter is a real collaborator here; "XYZ" has no hardcoded rate.
        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request().currency("XYZ").build()));

        assertEquals("UNSUPPORTED_CURRENCY", exception.getErrorCode());
    }

    @Test
    void processPayment_convertsNonUsdAmountForLimitChecks() {
        stubMerchantFound();
        stubDailyTotal(BigDecimal.ZERO);
        stubNoRecentPayments();
        stubSaveEchoesEntity();

        PaymentResponse response = paymentService.processPayment(
                request().amount(new BigDecimal("100.00")).currency("EUR").build());

        // The stored amount stays in the original currency; only limit checks use USD.
        assertEquals(new BigDecimal("100.00"), response.getAmount());
        assertEquals("EUR", response.getCurrency());
    }

    // ---------------------------------------------------------------
    // Wire transfers
    // ---------------------------------------------------------------

    @Test
    void processPayment_wireBelowMinimumIsRejected() {
        stubMerchantFound();

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request()
                        .paymentType(PaymentType.WIRE)
                        .amount(new BigDecimal("99.99"))
                        .customerName("Ada Lovelace")
                        .build()));

        assertEquals("WIRE_MINIMUM_NOT_MET", exception.getErrorCode());
    }

    @Test
    void processPayment_wireWithoutCustomerNameIsRejected() {
        stubMerchantFound();

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request()
                        .paymentType(PaymentType.WIRE)
                        .amount(new BigDecimal("500.00"))
                        .customerName("   ")
                        .build()));

        assertEquals("WIRE_NAME_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_wireAtMinimumWithCustomerNameIsAccepted() {
        stubMerchantFound();
        stubDailyTotal(BigDecimal.ZERO);
        stubSaveEchoesEntity();

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
    void processPayment_achBeyondDailyLimitReportsRemainingHeadroom() {
        stubMerchantFound();
        stubDailyTotal(new BigDecimal("24000.00"));

        InsufficientFundsException exception = assertThrows(InsufficientFundsException.class,
                () -> paymentService.processPayment(request()
                        .paymentType(PaymentType.ACH)
                        .amount(new BigDecimal("2000.00"))
                        .build()));

        assertEquals("INSUFFICIENT_FUNDS", exception.getErrorCode());
        assertEquals(new BigDecimal("2000.00"), exception.getRequestedAmount());
        assertEquals(new BigDecimal("1000.00"), exception.getAvailableAmount());
    }

    @Test
    void processPayment_achWithinDailyLimitIsAccepted() {
        stubMerchantFound();
        stubDailyTotal(new BigDecimal("1000.00"));
        stubSaveEchoesEntity();

        PaymentResponse response = paymentService.processPayment(request()
                .paymentType(PaymentType.ACH)
                .amount(new BigDecimal("500.00"))
                .build());

        assertNotNull(response.getTransactionId());
    }

    @Test
    void processPayment_treatsAbsentDailyTotalAsZero() {
        stubMerchantFound();
        stubDailyTotal(null);
        stubSaveEchoesEntity();

        PaymentResponse response = paymentService.processPayment(request()
                .paymentType(PaymentType.ACH)
                .amount(new BigDecimal("25000.00"))
                .build());

        assertNotNull(response.getTransactionId());
    }

    // ---------------------------------------------------------------
    // Card specific checks
    // ---------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = PaymentType.class, names = {"CREDIT_CARD", "DEBIT"})
    void processPayment_cardPaymentWithoutCardLastFourIsRejected(PaymentType type) {
        stubMerchantFound();

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request().paymentType(type).cardLastFour(null).build()));

        assertEquals("CARD_INFO_REQUIRED", exception.getErrorCode());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentType.class, names = {"CREDIT_CARD", "DEBIT"})
    void processPayment_cardPaymentWithMalformedCardLastFourIsRejected(PaymentType type) {
        stubMerchantFound();

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request().paymentType(type).cardLastFour("42").build()));

        assertEquals("CARD_INFO_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_debitPaymentIsAccepted() {
        stubMerchantFound();
        stubDailyTotal(BigDecimal.ZERO);
        stubSaveEchoesEntity();

        PaymentResponse response = paymentService.processPayment(
                request().paymentType(PaymentType.DEBIT).build());

        assertNotNull(response.getTransactionId());
    }

    @Test
    void processPayment_velocityCheckRejectsTenTransactionsInLastMinute() {
        stubMerchantFound();
        List<Payment> recent = IntStream.range(0, 10)
                .mapToObj(i -> Payment.builder().id((long) i).build())
                .collect(Collectors.toList());
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any())).thenReturn(recent);

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request().build()));

        assertEquals("VELOCITY_EXCEEDED", exception.getErrorCode());
    }

    // ---------------------------------------------------------------
    // Merchant daily limit
    // ---------------------------------------------------------------

    @Test
    void processPayment_merchantDailyLimitExceededReportsRemainingHeadroom() {
        merchant.setDailyLimit(new BigDecimal("150.00"));
        stubMerchantFound();
        stubDailyTotal(new BigDecimal("100.00"));
        stubNoRecentPayments();

        InsufficientFundsException exception = assertThrows(InsufficientFundsException.class,
                () -> paymentService.processPayment(request().build()));

        assertEquals(new BigDecimal("99.99"), exception.getRequestedAmount());
        assertEquals(new BigDecimal("50.00"), exception.getAvailableAmount());
    }

    @Test
    void processPayment_skipsMerchantLimitCheckWhenNoLimitConfigured() {
        merchant.setDailyLimit(null);
        stubMerchantFound();
        stubNoRecentPayments();
        stubSaveEchoesEntity();

        PaymentResponse response = paymentService.processPayment(request().build());

        assertNotNull(response.getTransactionId());
        verify(paymentRepository, org.mockito.Mockito.never())
                .sumCompletedAmountByMerchantSince(anyLong(), any());
    }

    // ---------------------------------------------------------------
    // Fees
    // ---------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
            "WIRE,   1000.00, 1.0000,  999.0000",
            "ACH,    1000.00, 8.0000,  992.0000"
    })
    void processPayment_appliesPaymentTypeFee(PaymentType type, String amount, String fee, String net) {
        stubMerchantFound();
        stubDailyTotal(BigDecimal.ZERO);
        stubSaveEchoesEntity();

        paymentService.processPayment(request()
                .paymentType(type)
                .amount(new BigDecimal(amount))
                .customerName("Ada Lovelace")
                .build());

        Payment saved = firstSavedPayment();
        assertEquals(new BigDecimal(fee), saved.getFeeAmount());
        assertEquals(new BigDecimal(net), saved.getNetAmount());
    }

    @Test
    void processPayment_appliesCreditCardFee() {
        stubMerchantFound();
        stubDailyTotal(BigDecimal.ZERO);
        stubNoRecentPayments();
        stubSaveEchoesEntity();

        paymentService.processPayment(request().build());

        Payment saved = firstSavedPayment();
        assertEquals(new BigDecimal("2.8997"), saved.getFeeAmount());
        assertEquals(new BigDecimal("97.0903"), saved.getNetAmount());
    }

    // ---------------------------------------------------------------
    // Gateway / notification resilience
    // ---------------------------------------------------------------

    @Test
    void processPayment_marksPaymentFailedWhenGatewayStageThrows() {
        stubMerchantFound();
        stubDailyTotal(BigDecimal.ZERO);
        stubNoRecentPayments();
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> {
                    Payment payment = invocation.getArgument(0);
                    payment.setId(1L);
                    return payment;
                })
                .thenThrow(new IllegalStateException("connection reset"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentService.processPayment(request().build());

        assertEquals(PaymentStatus.FAILED, response.getStatus());
        assertEquals("Processing error: connection reset", response.getFailureReason());
        assertNull(response.getGatewayReference());
    }

    @Test
    void processPayment_succeedsWhenNotificationFails() {
        stubMerchantFound();
        stubDailyTotal(BigDecimal.ZERO);
        stubNoRecentPayments();
        stubSaveEchoesEntity();
        doThrow(new IllegalStateException("notification backend down"))
                .when(notificationService).sendPaymentNotification(any(Payment.class));

        PaymentResponse response = paymentService.processPayment(request().build());

        assertNotNull(response.getTransactionId());
    }

    @Test
    void processPayment_notifiesAfterProcessing() {
        stubMerchantFound();
        stubDailyTotal(BigDecimal.ZERO);
        stubNoRecentPayments();
        stubSaveEchoesEntity();

        paymentService.processPayment(request().build());

        verify(notificationService).sendPaymentNotification(any(Payment.class));
    }

    // ---------------------------------------------------------------
    // Lookups
    // ---------------------------------------------------------------

    @Test
    void getPaymentById_mapsEntityToResponse() {
        Payment payment = Payment.builder()
                .id(4L)
                .transactionId("TXN-LOOKUP01")
                .merchantId(1L)
                .amount(new BigDecimal("12.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.ACH)
                .description("invoice 42")
                .customerEmail("customer@example.com")
                .feeAmount(new BigDecimal("0.0960"))
                .netAmount(new BigDecimal("11.9040"))
                .gatewayReference("GW-12345678")
                .build();
        when(paymentRepository.findById(4L)).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentById(4L);

        assertEquals(4L, response.getId());
        assertEquals("TXN-LOOKUP01", response.getTransactionId());
        assertEquals(PaymentStatus.COMPLETED, response.getStatus());
        assertEquals(PaymentType.ACH, response.getPaymentType());
        assertEquals("invoice 42", response.getDescription());
        assertEquals("GW-12345678", response.getGatewayReference());
    }

    @Test
    void getPaymentByTransactionId_returnsMatchingPayment() {
        Payment payment = Payment.builder().id(4L).transactionId("TXN-LOOKUP01")
                .status(PaymentStatus.COMPLETED).build();
        when(paymentRepository.findByTransactionId("TXN-LOOKUP01")).thenReturn(Optional.of(payment));

        assertEquals("TXN-LOOKUP01", paymentService.getPaymentByTransactionId("TXN-LOOKUP01").getTransactionId());
    }

    @Test
    void getPaymentByTransactionId_throwsWhenMissing() {
        when(paymentRepository.findByTransactionId("TXN-NOPE")).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.getPaymentByTransactionId("TXN-NOPE"));

        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void getPaymentsByMerchant_mapsEveryPayment() {
        when(paymentRepository.findByMerchantIdAndStatus(1L, null)).thenReturn(List.of(
                Payment.builder().id(1L).transactionId("TXN-1").status(PaymentStatus.COMPLETED).build(),
                Payment.builder().id(2L).transactionId("TXN-2").status(PaymentStatus.FAILED).build()));

        List<PaymentResponse> responses = paymentService.getPaymentsByMerchant(1L);

        assertEquals(List.of("TXN-1", "TXN-2"),
                responses.stream().map(PaymentResponse::getTransactionId).collect(Collectors.toList()));
    }

    @Test
    void getPaymentsByMerchant_returnsEmptyListWhenMerchantHasNoPayments() {
        when(paymentRepository.findByMerchantIdAndStatus(2L, null)).thenReturn(Collections.emptyList());

        assertEquals(List.of(), paymentService.getPaymentsByMerchant(2L));
    }

    // ---------------------------------------------------------------
    // Status transitions
    // ---------------------------------------------------------------

    @Test
    void updatePaymentStatus_setsCompletedAtWhenCompleting() {
        Payment payment = Payment.builder().id(1L).transactionId("TXN-1").status(PaymentStatus.PROCESSING).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        stubSaveEchoesEntity();

        PaymentResponse response = paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED);

        assertEquals(PaymentStatus.COMPLETED, response.getStatus());
        assertNotNull(response.getCompletedAt());
    }

    @Test
    void updatePaymentStatus_allowsRefundingATerminalPayment() {
        Payment payment = Payment.builder().id(1L).transactionId("TXN-1").status(PaymentStatus.COMPLETED).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        stubSaveEchoesEntity();

        PaymentResponse response = paymentService.updatePaymentStatus(1L, PaymentStatus.REFUNDED);

        assertEquals(PaymentStatus.REFUNDED, response.getStatus());
        assertNull(response.getCompletedAt());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"COMPLETED", "FAILED", "REFUNDED"})
    void updatePaymentStatus_rejectsTransitionsOutOfTerminalStates(PaymentStatus terminal) {
        Payment payment = Payment.builder().id(1L).transactionId("TXN-1").status(terminal).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.updatePaymentStatus(1L, PaymentStatus.PROCESSING));

        assertEquals("INVALID_STATE_TRANSITION", exception.getErrorCode());
    }

    @Test
    void updatePaymentStatus_throwsWhenPaymentMissing() {
        when(paymentRepository.findById(404L)).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.updatePaymentStatus(404L, PaymentStatus.COMPLETED));

        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void cancelPayment_throwsWhenPaymentMissing() {
        when(paymentRepository.findById(404L)).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.cancelPayment(404L));

        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void cancelPayment_recordsCancellationReason() {
        Payment payment = Payment.builder().id(1L).transactionId("TXN-1").status(PaymentStatus.PENDING).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        stubSaveEchoesEntity();

        paymentService.cancelPayment(1L);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertEquals("Cancelled by user", captor.getValue().getFailureReason());
        assertTrue(captor.getValue().getStatus() == PaymentStatus.FAILED);
    }
}
