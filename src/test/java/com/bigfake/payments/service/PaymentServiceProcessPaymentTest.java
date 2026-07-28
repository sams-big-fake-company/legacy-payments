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
import com.bigfake.payments.testsupport.TestFixtures;
import com.bigfake.payments.util.CurrencyConverter;
import com.bigfake.payments.util.PaymentValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Behavioural tests for {@link PaymentServiceImpl#processPayment}: idempotency,
 * merchant validation, currency normalisation, per-modality rules, limits and fees.
 *
 * <p>The gateway call is simulated inside the service with {@code Math.random()},
 * so the COMPLETED/FAILED outcome of a payment that reaches the gateway cannot be
 * forced. Assertions on those tests check the invariants that hold for either
 * outcome; everything before the gateway call is fully deterministic.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceProcessPaymentTest {

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
        merchant = TestFixtures.merchant().build();
    }

    private void givenActiveMerchant() {
        when(merchantRepository.findById(TestFixtures.MERCHANT_ID)).thenReturn(Optional.of(merchant));
    }

    private void givenNoPaymentsToday() {
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(null);
    }

    private void givenNoRecentPayments() {
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
    }

    private void givenPaymentsAreSaved() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment saved = invocation.getArgument(0);
            saved.setId(42L);
            return saved;
        });
    }

    /** Holds for both possible gateway outcomes of a payment that reached the gateway. */
    private void assertGatewayOutcomeIsConsistent(PaymentResponse response) {
        if (response.getStatus() == PaymentStatus.COMPLETED) {
            assertTrue(response.getGatewayReference().startsWith("GW-"),
                    "completed payments carry a gateway reference");
            assertNotNull(response.getCompletedAt());
            assertNull(response.getFailureReason());
        } else {
            assertEquals(PaymentStatus.FAILED, response.getStatus());
            assertEquals("Gateway declined the transaction", response.getFailureReason());
            assertNull(response.getGatewayReference());
        }
    }

    @Nested
    @DisplayName("idempotency")
    class Idempotency {

        @Test
        void returnsExistingPaymentWithoutReprocessingWhenKeyAlreadySeen() {
            Payment existing = TestFixtures.payment()
                    .transactionId("TXN-EXISTING")
                    .idempotencyKey("idem-1")
                    .gatewayReference("GW-abc123")
                    .build();
            when(paymentRepository.findByIdempotencyKey("idem-1")).thenReturn(Optional.of(existing));

            PaymentResponse response = paymentService.processPayment(
                    TestFixtures.creditCardRequest().idempotencyKey("idem-1").build());

            assertEquals("TXN-EXISTING", response.getTransactionId());
            assertEquals(PaymentStatus.COMPLETED, response.getStatus());
            assertEquals("GW-abc123", response.getGatewayReference());
            verifyNoInteractions(merchantRepository, notificationService);
            verify(paymentRepository, never()).save(any(Payment.class));
        }

        @Test
        void processesNormallyAndStoresKeyWhenKeyIsUnseen() {
            when(paymentRepository.findByIdempotencyKey("idem-2")).thenReturn(Optional.empty());
            givenActiveMerchant();
            givenNoPaymentsToday();
            givenNoRecentPayments();
            givenPaymentsAreSaved();

            paymentService.processPayment(
                    TestFixtures.creditCardRequest().idempotencyKey("idem-2").build());

            ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository, times(3)).save(saved.capture());
            assertEquals("idem-2", saved.getValue().getIdempotencyKey());
        }

        @ParameterizedTest(name = "idempotencyKey=\"{0}\" is ignored")
        @ValueSource(strings = {""})
        void blankKeyIsNotTreatedAsIdempotent(String key) {
            givenActiveMerchant();
            givenNoPaymentsToday();
            givenNoRecentPayments();
            givenPaymentsAreSaved();

            paymentService.processPayment(TestFixtures.creditCardRequest().idempotencyKey(key).build());

            verify(paymentRepository, never()).findByIdempotencyKey(any());
        }

        @Test
        void nullKeyIsNotTreatedAsIdempotent() {
            givenActiveMerchant();
            givenNoPaymentsToday();
            givenNoRecentPayments();
            givenPaymentsAreSaved();

            paymentService.processPayment(TestFixtures.creditCardRequest().idempotencyKey(null).build());

            verify(paymentRepository, never()).findByIdempotencyKey(any());
        }
    }

    @Nested
    @DisplayName("merchant and amount validation")
    class MerchantAndAmountValidation {

        @Test
        void rejectsUnknownMerchant() {
            when(merchantRepository.findById(TestFixtures.MERCHANT_ID)).thenReturn(Optional.empty());

            PaymentException exception = assertThrows(PaymentException.class,
                    () -> paymentService.processPayment(TestFixtures.creditCardRequest().build()));

            assertEquals("MERCHANT_NOT_FOUND", exception.getErrorCode());
            verify(paymentRepository, never()).save(any(Payment.class));
        }

        @Test
        void rejectsInactiveMerchant() {
            merchant.setIsActive(false);
            givenActiveMerchant();

            PaymentException exception = assertThrows(PaymentException.class,
                    () -> paymentService.processPayment(TestFixtures.creditCardRequest().build()));

            assertEquals("MERCHANT_INACTIVE", exception.getErrorCode());
        }

        @ParameterizedTest(name = "amount {0} is rejected with {1}")
        @CsvSource({
                "0.49, AMOUNT_TOO_LOW",
                "0.00, AMOUNT_TOO_LOW",
                "50000.01, AMOUNT_TOO_HIGH"
        })
        void rejectsAmountsOutsideTheAllowedRange(String amount, String expectedErrorCode) {
            givenActiveMerchant();

            PaymentException exception = assertThrows(PaymentException.class,
                    () -> paymentService.processPayment(
                            TestFixtures.creditCardRequest().amount(new BigDecimal(amount)).build()));

            assertEquals(expectedErrorCode, exception.getErrorCode());
        }

        @ParameterizedTest(name = "boundary amount {0} is accepted")
        @ValueSource(strings = {"0.50", "50000.00"})
        void acceptsBoundaryAmounts(String amount) {
            givenActiveMerchant();
            givenNoPaymentsToday();
            givenNoRecentPayments();
            givenPaymentsAreSaved();

            PaymentResponse response = paymentService.processPayment(
                    TestFixtures.creditCardRequest().amount(new BigDecimal(amount)).build());

            assertEquals(new BigDecimal(amount), response.getAmount());
        }
    }

    @Nested
    @DisplayName("currency normalisation")
    class CurrencyNormalisation {

        @Test
        void convertsNonUsdAmountToUsdForLimitChecks() {
            givenActiveMerchant();
            merchant.setDailyLimit(new BigDecimal("100.00"));
            when(currencyConverter.convertToUsd(new BigDecimal("100.00"), "EUR"))
                    .thenReturn(new BigDecimal("108.7500"));
            when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                    .thenReturn(BigDecimal.ZERO);

            InsufficientFundsException exception = assertThrows(InsufficientFundsException.class,
                    () -> paymentService.processPayment(TestFixtures.creditCardRequest()
                            .amount(new BigDecimal("100.00"))
                            .currency("EUR")
                            .build()));

            assertEquals(new BigDecimal("108.7500"), exception.getRequestedAmount());
            assertEquals(new BigDecimal("100.00"), exception.getAvailableAmount());
        }

        @Test
        void chargesFeesOnTheOriginalCurrencyAmountNotTheUsdEquivalent() {
            givenActiveMerchant();
            merchant.setDailyLimit(null);
            when(currencyConverter.convertToUsd(new BigDecimal("100.00"), "EUR"))
                    .thenReturn(new BigDecimal("108.7500"));
            givenNoRecentPayments();
            givenPaymentsAreSaved();

            PaymentResponse response = paymentService.processPayment(TestFixtures.creditCardRequest()
                    .amount(new BigDecimal("100.00"))
                    .currency("EUR")
                    .build());

            assertEquals(new BigDecimal("2.9000"), response.getFeeAmount());
            assertEquals("EUR", response.getCurrency());
        }

        @Test
        void rejectsCurrencyTheConverterDoesNotSupport() {
            givenActiveMerchant();
            when(currencyConverter.convertToUsd(any(), eq("XYZ"))).thenReturn(null);

            PaymentException exception = assertThrows(PaymentException.class,
                    () -> paymentService.processPayment(
                            TestFixtures.creditCardRequest().currency("XYZ").build()));

            assertEquals("UNSUPPORTED_CURRENCY", exception.getErrorCode());
        }

        @Test
        void skipsConversionForUsdAndForMissingCurrency() {
            givenActiveMerchant();
            merchant.setDailyLimit(null);
            givenNoRecentPayments();
            givenPaymentsAreSaved();

            paymentService.processPayment(TestFixtures.creditCardRequest().currency("USD").build());
            paymentService.processPayment(TestFixtures.creditCardRequest().currency(null).build());

            verifyNoInteractions(currencyConverter);
        }
    }

    @Nested
    @DisplayName("payment type rules")
    class PaymentTypeRules {

        @Test
        void wireBelowMinimumIsRejected() {
            givenActiveMerchant();

            PaymentException exception = assertThrows(PaymentException.class,
                    () -> paymentService.processPayment(TestFixtures.creditCardRequest()
                            .paymentType(PaymentType.WIRE)
                            .cardLastFour(null)
                            .amount(new BigDecimal("99.99"))
                            .customerName("Ada Lovelace")
                            .build()));

            assertEquals("WIRE_MINIMUM_NOT_MET", exception.getErrorCode());
        }

        @ParameterizedTest(name = "wire with customerName=\"{0}\" is rejected")
        @ValueSource(strings = {"", "   "})
        void wireWithoutCustomerNameIsRejected(String customerName) {
            givenActiveMerchant();

            PaymentException exception = assertThrows(PaymentException.class,
                    () -> paymentService.processPayment(TestFixtures.creditCardRequest()
                            .paymentType(PaymentType.WIRE)
                            .cardLastFour(null)
                            .amount(new BigDecimal("100.00"))
                            .customerName(customerName)
                            .build()));

            assertEquals("WIRE_NAME_REQUIRED", exception.getErrorCode());
        }

        @Test
        void wireWithNullCustomerNameIsRejected() {
            givenActiveMerchant();

            PaymentException exception = assertThrows(PaymentException.class,
                    () -> paymentService.processPayment(TestFixtures.creditCardRequest()
                            .paymentType(PaymentType.WIRE)
                            .cardLastFour(null)
                            .amount(new BigDecimal("500.00"))
                            .customerName(null)
                            .build()));

            assertEquals("WIRE_NAME_REQUIRED", exception.getErrorCode());
        }

        @Test
        void wireAtMinimumWithCustomerNameIsAccepted() {
            givenActiveMerchant();
            merchant.setDailyLimit(null);
            givenPaymentsAreSaved();

            PaymentResponse response = paymentService.processPayment(TestFixtures.creditCardRequest()
                    .paymentType(PaymentType.WIRE)
                    .cardLastFour(null)
                    .amount(new BigDecimal("100.00"))
                    .customerName("Ada Lovelace")
                    .build());

            assertEquals(new BigDecimal("0.1000"), response.getFeeAmount());
            assertGatewayOutcomeIsConsistent(response);
        }

        @Test
        void achAboveDailyLimitIsRejectedWithRemainingHeadroom() {
            givenActiveMerchant();
            when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                    .thenReturn(new BigDecimal("24000.00"));

            InsufficientFundsException exception = assertThrows(InsufficientFundsException.class,
                    () -> paymentService.processPayment(TestFixtures.creditCardRequest()
                            .paymentType(PaymentType.ACH)
                            .cardLastFour(null)
                            .amount(new BigDecimal("2000.00"))
                            .build()));

            assertEquals("INSUFFICIENT_FUNDS", exception.getErrorCode());
            assertEquals(new BigDecimal("2000.00"), exception.getRequestedAmount());
            assertEquals(new BigDecimal("1000.00"), exception.getAvailableAmount());
        }

        @Test
        void achExactlyAtDailyLimitIsAccepted() {
            givenActiveMerchant();
            merchant.setDailyLimit(null);
            when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                    .thenReturn(new BigDecimal("24000.00"));
            givenPaymentsAreSaved();

            PaymentResponse response = paymentService.processPayment(TestFixtures.creditCardRequest()
                    .paymentType(PaymentType.ACH)
                    .cardLastFour(null)
                    .amount(new BigDecimal("1000.00"))
                    .build());

            assertEquals(new BigDecimal("8.0000"), response.getFeeAmount());
            assertEquals(new BigDecimal("992.0000"), response.getNetAmount());
        }

        @ParameterizedTest(name = "{0} without usable card digits is rejected")
        @EnumSource(value = PaymentType.class, names = {"CREDIT_CARD", "DEBIT"})
        void cardPaymentsRequireExactlyFourCardDigits(PaymentType type) {
            givenActiveMerchant();

            PaymentException missing = assertThrows(PaymentException.class,
                    () -> paymentService.processPayment(TestFixtures.creditCardRequest()
                            .paymentType(type)
                            .cardLastFour(null)
                            .build()));
            PaymentException tooShort = assertThrows(PaymentException.class,
                    () -> paymentService.processPayment(TestFixtures.creditCardRequest()
                            .paymentType(type)
                            .cardLastFour("42")
                            .build()));

            assertEquals("CARD_INFO_REQUIRED", missing.getErrorCode());
            assertEquals("CARD_INFO_REQUIRED", tooShort.getErrorCode());
        }

        @Test
        void debitPaymentsSkipTheVelocityCheck() {
            givenActiveMerchant();
            merchant.setDailyLimit(null);
            givenPaymentsAreSaved();

            paymentService.processPayment(TestFixtures.creditCardRequest()
                    .paymentType(PaymentType.DEBIT)
                    .amount(new BigDecimal("200.00"))
                    .build());

            verify(paymentRepository, never()).findByMerchantIdAndDateRange(anyLong(), any(), any());
        }

        @ParameterizedTest(name = "fee for {0}")
        @CsvSource({
                "CREDIT_CARD, 2.9000",
                "DEBIT, 1.5000"
        })
        void appliesTheFeePercentageOfThePaymentType(PaymentType type, String expectedFee) {
            givenActiveMerchant();
            merchant.setDailyLimit(null);
            givenPaymentsAreSaved();
            if (type == PaymentType.CREDIT_CARD) {
                givenNoRecentPayments();
            }

            PaymentResponse response = paymentService.processPayment(TestFixtures.creditCardRequest()
                    .paymentType(type)
                    .amount(new BigDecimal("100.00"))
                    .build());

            assertEquals(new BigDecimal(expectedFee), response.getFeeAmount());
            assertEquals(new BigDecimal("100.00").subtract(new BigDecimal(expectedFee)),
                    response.getNetAmount());
        }
    }

    @Nested
    @DisplayName("velocity check")
    class VelocityCheck {

        private List<Payment> recentPayments(int count) {
            return IntStream.range(0, count)
                    .mapToObj(i -> TestFixtures.payment().id((long) i).build())
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        @Test
        void rejectsCreditCardWhenTenPaymentsInTheLastMinute() {
            givenActiveMerchant();
            when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                    .thenReturn(recentPayments(10));

            PaymentException exception = assertThrows(PaymentException.class,
                    () -> paymentService.processPayment(TestFixtures.creditCardRequest().build()));

            assertEquals("VELOCITY_EXCEEDED", exception.getErrorCode());
        }

        @Test
        void allowsCreditCardWhenBelowTheVelocityThreshold() {
            givenActiveMerchant();
            merchant.setDailyLimit(null);
            when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                    .thenReturn(recentPayments(9));
            givenPaymentsAreSaved();

            assertNotNull(paymentService.processPayment(TestFixtures.creditCardRequest().build()));
        }

        @Test
        void looksAtTheLastMinuteOnly() {
            givenActiveMerchant();
            merchant.setDailyLimit(null);
            givenNoRecentPayments();
            givenPaymentsAreSaved();
            LocalDateTime before = LocalDateTime.now().minusMinutes(1);

            paymentService.processPayment(TestFixtures.creditCardRequest().build());

            ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(paymentRepository).findByMerchantIdAndDateRange(
                    eq(TestFixtures.MERCHANT_ID), from.capture(), to.capture());
            assertFalse(from.getValue().isBefore(before));
            assertTrue(from.getValue().isBefore(to.getValue()));
        }
    }

    @Nested
    @DisplayName("merchant daily limit")
    class MerchantDailyLimit {

        @Test
        void rejectsPaymentThatWouldExceedTheMerchantDailyLimit() {
            givenActiveMerchant();
            merchant.setDailyLimit(new BigDecimal("500.00"));
            givenNoRecentPayments();
            when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                    .thenReturn(new BigDecimal("450.00"));

            InsufficientFundsException exception = assertThrows(InsufficientFundsException.class,
                    () -> paymentService.processPayment(
                            TestFixtures.creditCardRequest().amount(new BigDecimal("100.00")).build()));

            assertEquals(new BigDecimal("100.00"), exception.getRequestedAmount());
            assertEquals(new BigDecimal("50.00"), exception.getAvailableAmount());
            verify(paymentRepository, never()).save(any(Payment.class));
        }

        @Test
        void treatsMissingDailyTotalAsZero() {
            givenActiveMerchant();
            merchant.setDailyLimit(new BigDecimal("100.00"));
            givenNoRecentPayments();
            givenNoPaymentsToday();
            givenPaymentsAreSaved();

            PaymentResponse response = paymentService.processPayment(
                    TestFixtures.creditCardRequest().amount(new BigDecimal("100.00")).build());

            assertNotNull(response.getTransactionId());
        }

        @Test
        void skipsTheLimitCheckWhenMerchantHasNoDailyLimit() {
            givenActiveMerchant();
            merchant.setDailyLimit(null);
            givenNoRecentPayments();
            givenPaymentsAreSaved();

            paymentService.processPayment(TestFixtures.creditCardRequest().build());

            verify(paymentRepository, never()).sumCompletedAmountByMerchantSince(anyLong(), any());
        }
    }

    @Nested
    @DisplayName("persistence, gateway and notification")
    class PersistenceAndGateway {

        @Test
        void persistsTheRequestDetailsAndMovesThroughPendingAndProcessing() {
            givenActiveMerchant();
            merchant.setDailyLimit(null);
            givenNoRecentPayments();
            // The service mutates and re-saves one entity, so the status of each save has
            // to be recorded as it happens rather than captured afterwards.
            List<PaymentStatus> statusPerSave = new ArrayList<>();
            when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
                Payment saved = invocation.getArgument(0);
                statusPerSave.add(saved.getStatus());
                saved.setId(42L);
                return saved;
            });
            PaymentRequest request = TestFixtures.creditCardRequest()
                    .description("Order 1234")
                    .customerName("Ada Lovelace")
                    .metadata("{\"orderId\":1234}")
                    .build();

            PaymentResponse response = paymentService.processPayment(request);

            assertEquals(Arrays.asList(PaymentStatus.PENDING, PaymentStatus.PROCESSING, response.getStatus()),
                    statusPerSave);

            ArgumentCaptor<Payment> saves = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository, times(3)).save(saves.capture());
            Payment persisted = saves.getValue();
            assertEquals("Order 1234", persisted.getDescription());
            assertEquals("Ada Lovelace", persisted.getCustomerName());
            assertEquals("{\"orderId\":1234}", persisted.getMetadata());
            assertEquals("4242", persisted.getCardLastFour());
            assertEquals(new BigDecimal("2.8997"), persisted.getFeeAmount());
            assertEquals(new BigDecimal("97.0903"), persisted.getNetAmount());

            assertTrue(response.getTransactionId().startsWith("TXN-"));
            assertEquals(20, response.getTransactionId().length());
            assertEquals(42L, response.getId());
            assertEquals("customer@example.com", response.getCustomerEmail());
            assertGatewayOutcomeIsConsistent(response);
        }

        @Test
        void generatesAUniqueTransactionIdPerPayment() {
            givenActiveMerchant();
            merchant.setDailyLimit(null);
            givenNoRecentPayments();
            givenPaymentsAreSaved();

            String first = paymentService.processPayment(TestFixtures.creditCardRequest().build())
                    .getTransactionId();
            String second = paymentService.processPayment(TestFixtures.creditCardRequest().build())
                    .getTransactionId();

            assertFalse(first.equals(second));
        }

        @Test
        void marksThePaymentFailedWhenGatewayProcessingThrows() {
            givenActiveMerchant();
            merchant.setDailyLimit(null);
            givenNoRecentPayments();
            when(paymentRepository.save(any(Payment.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0))
                    .thenThrow(new IllegalStateException("connection reset"))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            PaymentResponse response = paymentService.processPayment(
                    TestFixtures.creditCardRequest().build());

            assertEquals(PaymentStatus.FAILED, response.getStatus());
            assertEquals("Processing error: connection reset", response.getFailureReason());
        }

        @Test
        void notifiesAfterProcessing() {
            givenActiveMerchant();
            merchant.setDailyLimit(null);
            givenNoRecentPayments();
            givenPaymentsAreSaved();

            PaymentResponse response = paymentService.processPayment(
                    TestFixtures.creditCardRequest().build());

            ArgumentCaptor<Payment> notified = ArgumentCaptor.forClass(Payment.class);
            verify(notificationService).sendPaymentNotification(notified.capture());
            assertEquals(response.getTransactionId(), notified.getValue().getTransactionId());
        }

        @Test
        void stillReturnsThePaymentWhenNotificationFails() {
            givenActiveMerchant();
            merchant.setDailyLimit(null);
            givenNoRecentPayments();
            givenPaymentsAreSaved();
            doThrow(new IllegalStateException("webhook endpoint down"))
                    .when(notificationService).sendPaymentNotification(any(Payment.class));

            PaymentResponse response = paymentService.processPayment(
                    TestFixtures.creditCardRequest().build());

            assertNotNull(response.getTransactionId());
            assertGatewayOutcomeIsConsistent(response);
        }

        @Test
        void neverConsultsThePaymentValidatorDuringProcessing() {
            givenActiveMerchant();
            merchant.setDailyLimit(null);
            givenNoRecentPayments();
            givenPaymentsAreSaved();

            paymentService.processPayment(TestFixtures.creditCardRequest().build());

            verifyNoInteractions(paymentValidator);
        }
    }
}
