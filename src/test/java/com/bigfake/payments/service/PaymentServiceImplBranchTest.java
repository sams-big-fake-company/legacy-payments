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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Branch-focused unit tests for PaymentServiceImpl, complementing
 * {@link PaymentServiceImplTest} which covers the main happy path and merchant checks.
 *
 * The simulated gateway call in the service has a random outcome, so these tests assert on
 * behaviour that is independent of the gateway result (fees, persisted fields, notifications).
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
                .customerEmail("customer@example.com");
    }

    private void stubMerchantFound() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
    }

    private void stubNoDailyVolume() {
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any())).thenReturn(BigDecimal.ZERO);
    }

    private void stubNoRecentPayments() {
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
    }

    private void stubSaveAssigningId() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(1L);
            return payment;
        });
    }

    private Payment lastSavedPayment() {
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    @Nested
    class CurrencyHandling {

        @Test
        void nonUsdCurrencyIsConvertedForLimitChecks() {
            stubMerchantFound();
            when(currencyConverter.convertToUsd(new BigDecimal("500.00"), "EUR"))
                    .thenReturn(new BigDecimal("543.75"));
            stubNoDailyVolume();
            stubNoRecentPayments();
            stubSaveAssigningId();

            PaymentResponse response = paymentService.processPayment(request().currency("EUR").build());

            assertEquals("EUR", response.getCurrency());
            // Fees are charged on the original amount, not the converted one.
            assertEquals(new BigDecimal("14.5000"), response.getFeeAmount());
            verify(currencyConverter).convertToUsd(new BigDecimal("500.00"), "EUR");
        }

        @Test
        void unsupportedCurrencyIsRejected() {
            stubMerchantFound();
            when(currencyConverter.convertToUsd(any(), eq("ZZZ"))).thenReturn(null);

            PaymentException exception = assertThrows(PaymentException.class,
                    () -> paymentService.processPayment(request().currency("ZZZ").build()));

            assertEquals("UNSUPPORTED_CURRENCY", exception.getErrorCode());
            verify(paymentRepository, never()).save(any(Payment.class));
        }

        @Test
        void usdPaymentsSkipConversion() {
            stubMerchantFound();
            stubNoDailyVolume();
            stubNoRecentPayments();
            stubSaveAssigningId();

            paymentService.processPayment(request().build());

            verify(currencyConverter, never()).convertToUsd(any(), any());
        }
    }

    @Nested
    class WireTransfers {

        @Test
        void belowWireMinimumIsRejected() {
            stubMerchantFound();

            PaymentException exception = assertThrows(PaymentException.class, () -> paymentService.processPayment(
                    request().paymentType(PaymentType.WIRE).amount(new BigDecimal("99.99"))
                            .customerName("Ada Lovelace").cardLastFour(null).build()));

            assertEquals("WIRE_MINIMUM_NOT_MET", exception.getErrorCode());
        }

        @Test
        void wireWithoutCustomerNameIsRejected() {
            stubMerchantFound();

            PaymentException exception = assertThrows(PaymentException.class, () -> paymentService.processPayment(
                    request().paymentType(PaymentType.WIRE).cardLastFour(null).build()));

            assertEquals("WIRE_NAME_REQUIRED", exception.getErrorCode());
        }

        @Test
        void wireWithBlankCustomerNameIsRejected() {
            stubMerchantFound();

            PaymentException exception = assertThrows(PaymentException.class, () -> paymentService.processPayment(
                    request().paymentType(PaymentType.WIRE).customerName("   ").cardLastFour(null).build()));

            assertEquals("WIRE_NAME_REQUIRED", exception.getErrorCode());
        }

        @Test
        void validWireIsProcessedWithWireFee() {
            stubMerchantFound();
            stubNoDailyVolume();
            stubSaveAssigningId();

            PaymentResponse response = paymentService.processPayment(
                    request().paymentType(PaymentType.WIRE).customerName("Ada Lovelace")
                            .cardLastFour(null).build());

            assertEquals(new BigDecimal("0.5000"), response.getFeeAmount());
            assertEquals(new BigDecimal("499.5000"), response.getNetAmount());
        }
    }

    @Nested
    class AchTransfers {

        @Test
        void withinDailyLimitIsProcessed() {
            stubMerchantFound();
            stubNoDailyVolume();
            stubSaveAssigningId();

            PaymentResponse response = paymentService.processPayment(
                    request().paymentType(PaymentType.ACH).cardLastFour(null).build());

            assertEquals(new BigDecimal("4.0000"), response.getFeeAmount());
        }

        @Test
        void exceedingAchDailyLimitThrowsInsufficientFunds() {
            stubMerchantFound();
            when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                    .thenReturn(new BigDecimal("24800.00"));

            InsufficientFundsException exception = assertThrows(InsufficientFundsException.class,
                    () -> paymentService.processPayment(
                            request().paymentType(PaymentType.ACH).cardLastFour(null).build()));

            assertEquals("INSUFFICIENT_FUNDS", exception.getErrorCode());
            assertEquals(new BigDecimal("500.00"), exception.getRequestedAmount());
            assertEquals(new BigDecimal("200.00"), exception.getAvailableAmount());
        }

        @Test
        void nullDailyVolumeIsTreatedAsZero() {
            stubMerchantFound();
            when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any())).thenReturn(null);
            stubSaveAssigningId();

            PaymentResponse response = paymentService.processPayment(
                    request().paymentType(PaymentType.ACH).cardLastFour(null).build());

            assertNotNull(response.getTransactionId());
        }
    }

    @Nested
    class CardPayments {

        @ParameterizedTest
        @CsvSource({"CREDIT_CARD", "DEBIT"})
        void missingCardLastFourIsRejected(PaymentType type) {
            stubMerchantFound();

            PaymentException exception = assertThrows(PaymentException.class,
                    () -> paymentService.processPayment(request().paymentType(type).cardLastFour(null).build()));

            assertEquals("CARD_INFO_REQUIRED", exception.getErrorCode());
        }

        @ParameterizedTest
        @CsvSource({"CREDIT_CARD", "DEBIT"})
        void wrongLengthCardLastFourIsRejected(PaymentType type) {
            stubMerchantFound();

            PaymentException exception = assertThrows(PaymentException.class,
                    () -> paymentService.processPayment(request().paymentType(type).cardLastFour("123").build()));

            assertEquals("CARD_INFO_REQUIRED", exception.getErrorCode());
        }

        @Test
        void debitPaymentsSkipTheVelocityCheck() {
            stubMerchantFound();
            stubNoDailyVolume();
            stubSaveAssigningId();

            PaymentResponse response = paymentService.processPayment(
                    request().paymentType(PaymentType.DEBIT).build());

            assertEquals(new BigDecimal("7.5000"), response.getFeeAmount());
            verify(paymentRepository, never()).findByMerchantIdAndDateRange(anyLong(), any(), any());
        }

        @Test
        void velocityLimitIsEnforcedForCreditCards() {
            stubMerchantFound();
            List<Payment> recent = IntStream.range(0, 10)
                    .mapToObj(i -> Payment.builder().id((long) i).build())
                    .collect(Collectors.toList());
            when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any())).thenReturn(recent);

            PaymentException exception = assertThrows(PaymentException.class,
                    () -> paymentService.processPayment(request().build()));

            assertEquals("VELOCITY_EXCEEDED", exception.getErrorCode());
        }

        @Test
        void justBelowVelocityLimitIsAllowed() {
            stubMerchantFound();
            List<Payment> recent = IntStream.range(0, 9)
                    .mapToObj(i -> Payment.builder().id((long) i).build())
                    .collect(Collectors.toList());
            when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any())).thenReturn(recent);
            stubNoDailyVolume();
            stubSaveAssigningId();

            assertNotNull(paymentService.processPayment(request().build()).getTransactionId());
        }
    }

    @Nested
    class MerchantLimits {

        @Test
        void exceedingMerchantDailyLimitThrowsInsufficientFunds() {
            merchant.setDailyLimit(new BigDecimal("600.00"));
            stubMerchantFound();
            stubNoRecentPayments();
            when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                    .thenReturn(new BigDecimal("300.00"));

            InsufficientFundsException exception = assertThrows(InsufficientFundsException.class,
                    () -> paymentService.processPayment(request().build()));

            assertEquals(new BigDecimal("300.00"), exception.getAvailableAmount());
        }

        @Test
        void merchantWithoutDailyLimitSkipsTheCheck() {
            merchant.setDailyLimit(null);
            stubMerchantFound();
            stubNoRecentPayments();
            stubSaveAssigningId();

            assertNotNull(paymentService.processPayment(request().build()));
            verify(paymentRepository, never()).sumCompletedAmountByMerchantSince(anyLong(), any());
        }
    }

    @Test
    void processPayment_persistsRequestFieldsOnThePaymentEntity() {
        stubMerchantFound();
        stubNoDailyVolume();
        stubNoRecentPayments();
        stubSaveAssigningId();

        paymentService.processPayment(request()
                .description("Order 1234")
                .customerName("Ada Lovelace")
                .idempotencyKey("idem-1")
                .metadata("{\"source\":\"web\"}")
                .build());

        Payment saved = lastSavedPayment();
        assertEquals("Order 1234", saved.getDescription());
        assertEquals("Ada Lovelace", saved.getCustomerName());
        assertEquals("idem-1", saved.getIdempotencyKey());
        assertEquals("{\"source\":\"web\"}", saved.getMetadata());
        assertEquals("4242", saved.getCardLastFour());
        assertTrue(saved.getTransactionId().startsWith("TXN-"));
        assertEquals(20, saved.getTransactionId().length());
    }

    @Test
    void processPayment_emptyIdempotencyKeySkipsLookup() {
        stubMerchantFound();
        stubNoDailyVolume();
        stubNoRecentPayments();
        stubSaveAssigningId();

        paymentService.processPayment(request().idempotencyKey("").build());

        verify(paymentRepository, never()).findByIdempotencyKey(any());
    }

    @Test
    void processPayment_notificationFailureDoesNotFailThePayment() {
        stubMerchantFound();
        stubNoDailyVolume();
        stubNoRecentPayments();
        stubSaveAssigningId();
        doThrow(new IllegalStateException("notification service down"))
                .when(notificationService).sendPaymentNotification(any(Payment.class));

        PaymentResponse response = paymentService.processPayment(request().build());

        assertNotNull(response.getTransactionId());
    }

    @Test
    void processPayment_reachesATerminalGatewayOutcome() {
        stubMerchantFound();
        stubNoDailyVolume();
        stubNoRecentPayments();
        stubSaveAssigningId();

        PaymentResponse response = paymentService.processPayment(request().build());

        // The simulated gateway either approves or declines; both are terminal states.
        assertTrue(response.getStatus().isTerminal());
        if (response.getStatus() == PaymentStatus.COMPLETED) {
            assertNotNull(response.getCompletedAt());
            assertTrue(response.getGatewayReference().startsWith("GW-"));
            assertNull(response.getFailureReason());
        } else {
            assertEquals(PaymentStatus.FAILED, response.getStatus());
            assertEquals("Gateway declined the transaction", response.getFailureReason());
        }
        verify(notificationService).sendPaymentNotification(any(Payment.class));
    }

    @Test
    void getPaymentByTransactionId_returnsMappedResponse() {
        Payment payment = Payment.builder()
                .id(3L)
                .transactionId("TXN-LOOKUP1")
                .merchantId(1L)
                .amount(new BigDecimal("12.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.ACH)
                .build();
        when(paymentRepository.findByTransactionId("TXN-LOOKUP1")).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentByTransactionId("TXN-LOOKUP1");

        assertEquals(3L, response.getId());
        assertEquals("TXN-LOOKUP1", response.getTransactionId());
        assertEquals(PaymentType.ACH, response.getPaymentType());
    }

    @Test
    void getPaymentByTransactionId_notFoundThrows() {
        when(paymentRepository.findByTransactionId("TXN-MISSING")).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.getPaymentByTransactionId("TXN-MISSING"));

        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void getPaymentById_returnsMappedResponse() {
        Payment payment = Payment.builder()
                .id(4L)
                .transactionId("TXN-BYID1")
                .amount(new BigDecimal("8.00"))
                .status(PaymentStatus.PENDING)
                .build();
        when(paymentRepository.findById(4L)).thenReturn(Optional.of(payment));

        assertEquals("TXN-BYID1", paymentService.getPaymentById(4L).getTransactionId());
    }

    @Test
    void getPaymentsByMerchant_mapsEveryPayment() {
        when(paymentRepository.findByMerchantIdAndStatus(1L, null)).thenReturn(List.of(
                Payment.builder().id(1L).transactionId("TXN-1").build(),
                Payment.builder().id(2L).transactionId("TXN-2").build()));

        List<PaymentResponse> responses = paymentService.getPaymentsByMerchant(1L);

        assertEquals(List.of("TXN-1", "TXN-2"),
                responses.stream().map(PaymentResponse::getTransactionId).collect(Collectors.toList()));
    }

    @Test
    void getPaymentsByMerchant_noPaymentsReturnsEmptyList() {
        when(paymentRepository.findByMerchantIdAndStatus(2L, null)).thenReturn(Collections.emptyList());

        assertEquals(List.of(), paymentService.getPaymentsByMerchant(2L));
    }

    @Nested
    class StatusUpdates {

        @Test
        void completingAPaymentStampsCompletedAt() {
            Payment payment = Payment.builder().id(1L).status(PaymentStatus.PROCESSING).build();
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

            PaymentResponse response = paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED);

            assertEquals(PaymentStatus.COMPLETED, response.getStatus());
            assertNotNull(response.getCompletedAt());
        }

        @Test
        void nonCompletingTransitionDoesNotStampCompletedAt() {
            Payment payment = Payment.builder().id(1L).status(PaymentStatus.PENDING).build();
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

            PaymentResponse response = paymentService.updatePaymentStatus(1L, PaymentStatus.PROCESSING);

            assertEquals(PaymentStatus.PROCESSING, response.getStatus());
            assertNull(response.getCompletedAt());
        }

        @Test
        void completedPaymentsMayStillBeRefunded() {
            Payment payment = Payment.builder().id(1L).status(PaymentStatus.COMPLETED).build();
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

            assertEquals(PaymentStatus.REFUNDED,
                    paymentService.updatePaymentStatus(1L, PaymentStatus.REFUNDED).getStatus());
        }

        @ParameterizedTest
        @EnumSource(value = PaymentStatus.class, names = {"COMPLETED", "FAILED", "REFUNDED"})
        void terminalPaymentsCannotTransitionToNonRefundStatuses(PaymentStatus terminal) {
            Payment payment = Payment.builder().id(1L).status(terminal).build();
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            PaymentException exception = assertThrows(PaymentException.class,
                    () -> paymentService.updatePaymentStatus(1L, PaymentStatus.PROCESSING));

            assertEquals("INVALID_STATE_TRANSITION", exception.getErrorCode());
            verify(paymentRepository, never()).save(any(Payment.class));
        }

        @Test
        void unknownPaymentThrows() {
            when(paymentRepository.findById(404L)).thenReturn(Optional.empty());

            PaymentException exception = assertThrows(PaymentException.class,
                    () -> paymentService.updatePaymentStatus(404L, PaymentStatus.COMPLETED));

            assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
        }
    }

    @Test
    void cancelPayment_setsFailureReason() {
        Payment payment = Payment.builder().id(1L).transactionId("TXN-CANCEL1")
                .status(PaymentStatus.PENDING).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        paymentService.cancelPayment(1L);

        assertEquals("Cancelled by user", payment.getFailureReason());
        assertEquals(PaymentStatus.FAILED, payment.getStatus());
    }

    @Test
    void cancelPayment_unknownPaymentThrows() {
        when(paymentRepository.findById(404L)).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.cancelPayment(404L));

        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void processPayment_idempotentHitSkipsGatewayAndPersistence() {
        Payment existing = Payment.builder()
                .id(9L)
                .transactionId("TXN-EXISTING1")
                .merchantId(1L)
                .amount(new BigDecimal("500.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .completedAt(LocalDateTime.now())
                .build();
        when(paymentRepository.findByIdempotencyKey("idem-1")).thenReturn(Optional.of(existing));

        PaymentResponse response = paymentService.processPayment(request().idempotencyKey("idem-1").build());

        assertEquals("TXN-EXISTING1", response.getTransactionId());
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(notificationService, never()).sendPaymentNotification(any(Payment.class));
    }
}
