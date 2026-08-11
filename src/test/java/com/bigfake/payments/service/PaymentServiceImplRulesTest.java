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
import org.junit.jupiter.params.provider.EnumSource;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the payment-type, limit and lifecycle rules in PaymentServiceImpl
 * that are not covered by {@link PaymentServiceImplTest}.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplRulesTest {

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

    private PaymentRequest requestOf(PaymentType type, String amount) {
        return PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal(amount))
                .currency("USD")
                .paymentType(type)
                .customerName("Jane Buyer")
                .cardLastFour(type == PaymentType.CREDIT_CARD || type == PaymentType.DEBIT ? "4242" : null)
                .build();
    }

    private void stubMerchantAndSave() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(1L);
            return payment;
        });
    }

    private void stubNoDailyVolume() {
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any())).thenReturn(BigDecimal.ZERO);
    }

    private void stubNoRecentPayments() {
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
    }

    private Payment lastSavedPayment() {
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void processPayment_nonUsdCurrency_isConvertedForLimitChecks() {
        PaymentRequest request = requestOf(PaymentType.WIRE, "150.00");
        request.setCurrency("EUR");
        stubMerchantAndSave();
        stubNoDailyVolume();
        when(currencyConverter.convertToUsd(new BigDecimal("150.00"), "EUR")).thenReturn(new BigDecimal("163.13"));

        PaymentResponse response = paymentService.processPayment(request);

        assertEquals("EUR", response.getCurrency());
        assertEquals(new BigDecimal("150.00"), response.getAmount());
        verify(currencyConverter).convertToUsd(new BigDecimal("150.00"), "EUR");
    }

    @Test
    void processPayment_unsupportedCurrency_throwsUnsupportedCurrency() {
        PaymentRequest request = requestOf(PaymentType.WIRE, "150.00");
        request.setCurrency("ZZZ");
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        when(currencyConverter.convertToUsd(new BigDecimal("150.00"), "ZZZ")).thenReturn(null);

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));

        assertEquals("UNSUPPORTED_CURRENCY", exception.getErrorCode());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void processPayment_wireBelowMinimum_throwsWireMinimumNotMet() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(requestOf(PaymentType.WIRE, "99.99")));

        assertEquals("WIRE_MINIMUM_NOT_MET", exception.getErrorCode());
    }

    @Test
    void processPayment_wireWithoutCustomerName_throwsWireNameRequired() {
        PaymentRequest request = requestOf(PaymentType.WIRE, "500.00");
        request.setCustomerName("   ");
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));

        assertEquals("WIRE_NAME_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_wireAtMinimum_isAccepted() {
        stubMerchantAndSave();
        stubNoDailyVolume();

        PaymentResponse response = paymentService.processPayment(requestOf(PaymentType.WIRE, "100.00"));

        assertNotNull(response.getTransactionId());
        assertEquals(new BigDecimal("0.1000"), response.getFeeAmount());
        assertEquals(new BigDecimal("99.9000"), response.getNetAmount());
    }

    @Test
    void processPayment_achWithinDailyLimit_isAccepted() {
        stubMerchantAndSave();
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(new BigDecimal("20000.00"));

        PaymentResponse response = paymentService.processPayment(requestOf(PaymentType.ACH, "5000.00"));

        assertEquals(new BigDecimal("40.0000"), response.getFeeAmount());
        assertEquals(new BigDecimal("4960.0000"), response.getNetAmount());
    }

    @Test
    void processPayment_achAboveDailyLimit_throwsInsufficientFunds() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(new BigDecimal("24000.00"));

        InsufficientFundsException exception = assertThrows(InsufficientFundsException.class,
                () -> paymentService.processPayment(requestOf(PaymentType.ACH, "2000.00")));

        assertEquals("INSUFFICIENT_FUNDS", exception.getErrorCode());
        assertEquals(new BigDecimal("2000.00"), exception.getRequestedAmount());
        assertEquals(new BigDecimal("1000.00"), exception.getAvailableAmount());
    }

    @Test
    void processPayment_achWithNoPriorVolume_treatsNullTotalAsZero() {
        stubMerchantAndSave();
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any())).thenReturn(null);

        assertNotNull(paymentService.processPayment(requestOf(PaymentType.ACH, "100.00")).getTransactionId());
    }

    @Test
    void processPayment_creditCardWithoutCardLastFour_throwsCardInfoRequired() {
        PaymentRequest request = requestOf(PaymentType.CREDIT_CARD, "50.00");
        request.setCardLastFour(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));

        assertEquals("CARD_INFO_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_debitWithMalformedCardLastFour_throwsCardInfoRequired() {
        PaymentRequest request = requestOf(PaymentType.DEBIT, "50.00");
        request.setCardLastFour("42");
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));

        assertEquals("CARD_INFO_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_debitPayment_usesDebitFeeRate() {
        stubMerchantAndSave();
        stubNoDailyVolume();

        PaymentResponse response = paymentService.processPayment(requestOf(PaymentType.DEBIT, "200.00"));

        assertEquals(new BigDecimal("3.0000"), response.getFeeAmount());
        assertEquals(new BigDecimal("197.0000"), response.getNetAmount());
    }

    @Test
    void processPayment_creditCardVelocityExceeded_throwsVelocityExceeded() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        List<Payment> recent = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            recent.add(Payment.builder().id((long) i).build());
        }
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any())).thenReturn(recent);

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(requestOf(PaymentType.CREDIT_CARD, "50.00")));

        assertEquals("VELOCITY_EXCEEDED", exception.getErrorCode());
    }

    @Test
    void processPayment_creditCardBelowVelocityThreshold_isAccepted() {
        stubMerchantAndSave();
        stubNoDailyVolume();
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Arrays.asList(Payment.builder().id(1L).build(), Payment.builder().id(2L).build()));

        assertNotNull(paymentService.processPayment(requestOf(PaymentType.CREDIT_CARD, "50.00")).getTransactionId());
    }

    @Test
    void processPayment_merchantDailyLimitExceeded_throwsInsufficientFunds() {
        merchant.setDailyLimit(new BigDecimal("1000.00"));
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        stubNoRecentPayments();
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(new BigDecimal("990.00"));

        InsufficientFundsException exception = assertThrows(InsufficientFundsException.class,
                () -> paymentService.processPayment(requestOf(PaymentType.CREDIT_CARD, "50.00")));

        assertEquals(new BigDecimal("10.00"), exception.getAvailableAmount());
    }

    @Test
    void processPayment_merchantWithoutDailyLimit_skipsLimitCheck() {
        merchant.setDailyLimit(null);
        stubMerchantAndSave();
        stubNoRecentPayments();

        assertNotNull(paymentService.processPayment(requestOf(PaymentType.CREDIT_CARD, "50.00")).getTransactionId());
        verify(paymentRepository, never()).sumCompletedAmountByMerchantSince(anyLong(), any());
    }

    @Test
    void processPayment_persistsRequestFieldsOnPaymentEntity() {
        PaymentRequest request = requestOf(PaymentType.CREDIT_CARD, "120.00");
        request.setDescription("Order 4711");
        request.setCustomerEmail("buyer@example.com");
        request.setIdempotencyKey("idem-1");
        request.setMetadata("{\"channel\":\"web\"}");
        when(paymentRepository.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());
        stubMerchantAndSave();
        stubNoDailyVolume();
        stubNoRecentPayments();

        paymentService.processPayment(request);

        Payment saved = lastSavedPayment();
        assertEquals("Order 4711", saved.getDescription());
        assertEquals("buyer@example.com", saved.getCustomerEmail());
        assertEquals("Jane Buyer", saved.getCustomerName());
        assertEquals("4242", saved.getCardLastFour());
        assertEquals("idem-1", saved.getIdempotencyKey());
        assertEquals("{\"channel\":\"web\"}", saved.getMetadata());
        assertTrue(saved.getTransactionId().startsWith("TXN-"));
    }

    @Test
    void processPayment_emptyIdempotencyKey_skipsLookup() {
        PaymentRequest request = requestOf(PaymentType.CREDIT_CARD, "20.00");
        request.setIdempotencyKey("");
        stubMerchantAndSave();
        stubNoDailyVolume();
        stubNoRecentPayments();

        paymentService.processPayment(request);

        verify(paymentRepository, never()).findByIdempotencyKey(any());
    }

    @Test
    void processPayment_terminalOutcome_isEitherCompletedOrFailed() {
        stubMerchantAndSave();
        stubNoDailyVolume();
        stubNoRecentPayments();

        PaymentResponse response = paymentService.processPayment(requestOf(PaymentType.CREDIT_CARD, "20.00"));

        assertTrue(response.getStatus() == PaymentStatus.COMPLETED || response.getStatus() == PaymentStatus.FAILED);
        if (response.getStatus() == PaymentStatus.COMPLETED) {
            assertTrue(response.getGatewayReference().startsWith("GW-"));
            assertNull(response.getFailureReason());
        } else {
            assertEquals("Gateway declined the transaction", response.getFailureReason());
        }
    }

    @Test
    void processPayment_notificationFailure_doesNotFailPayment() {
        stubMerchantAndSave();
        stubNoDailyVolume();
        stubNoRecentPayments();
        doThrow(new RuntimeException("notification service down"))
                .when(notificationService).sendPaymentNotification(any(Payment.class));

        assertNotNull(paymentService.processPayment(requestOf(PaymentType.CREDIT_CARD, "20.00")).getTransactionId());
    }

    @Test
    void processPayment_repositoryFailureDuringGatewayStep_marksPaymentFailed() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        stubNoDailyVolume();
        stubNoRecentPayments();
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0))
                .thenThrow(new RuntimeException("db write timeout"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentService.processPayment(requestOf(PaymentType.CREDIT_CARD, "20.00"));

        assertEquals(PaymentStatus.FAILED, response.getStatus());
        assertEquals("Processing error: db write timeout", response.getFailureReason());
    }

    @Test
    void getPaymentById_existingPayment_isMappedToResponse() {
        Payment payment = Payment.builder()
                .id(4L)
                .transactionId("TXN-4")
                .merchantId(1L)
                .amount(new BigDecimal("15.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.ACH)
                .feeAmount(new BigDecimal("0.12"))
                .netAmount(new BigDecimal("14.88"))
                .gatewayReference("GW-1234")
                .build();
        when(paymentRepository.findById(4L)).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentById(4L);

        assertEquals(4L, response.getId());
        assertEquals("TXN-4", response.getTransactionId());
        assertEquals(PaymentType.ACH, response.getPaymentType());
        assertEquals("GW-1234", response.getGatewayReference());
    }

    @Test
    void getPaymentByTransactionId_existingPayment_isReturned() {
        when(paymentRepository.findByTransactionId("TXN-9")).thenReturn(Optional.of(
                Payment.builder().id(9L).transactionId("TXN-9").status(PaymentStatus.PENDING).build()));

        assertEquals("TXN-9", paymentService.getPaymentByTransactionId("TXN-9").getTransactionId());
    }

    @Test
    void getPaymentByTransactionId_missingPayment_throwsPaymentNotFound() {
        when(paymentRepository.findByTransactionId("TXN-MISSING")).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.getPaymentByTransactionId("TXN-MISSING"));

        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void getPaymentsByMerchant_mapsAllPayments() {
        when(paymentRepository.findByMerchantIdAndStatus(1L, null)).thenReturn(Arrays.asList(
                Payment.builder().id(1L).transactionId("TXN-1").status(PaymentStatus.COMPLETED).build(),
                Payment.builder().id(2L).transactionId("TXN-2").status(PaymentStatus.FAILED).build()));

        List<PaymentResponse> responses = paymentService.getPaymentsByMerchant(1L);

        assertEquals(2, responses.size());
        assertEquals("TXN-1", responses.get(0).getTransactionId());
        assertEquals(PaymentStatus.FAILED, responses.get(1).getStatus());
    }

    @Test
    void getPaymentsByMerchant_noPayments_returnsEmptyList() {
        when(paymentRepository.findByMerchantIdAndStatus(2L, null)).thenReturn(Collections.emptyList());

        assertEquals(List.of(), paymentService.getPaymentsByMerchant(2L));
    }

    @Test
    void updatePaymentStatus_toCompleted_setsCompletedAt() {
        Payment payment = Payment.builder().id(1L).transactionId("TXN-1").status(PaymentStatus.PROCESSING).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED);

        assertEquals(PaymentStatus.COMPLETED, response.getStatus());
        assertNotNull(response.getCompletedAt());
    }

    @Test
    void updatePaymentStatus_completedToRefunded_isAllowed() {
        Payment payment = Payment.builder().id(1L).transactionId("TXN-1").status(PaymentStatus.COMPLETED).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(PaymentStatus.REFUNDED, paymentService.updatePaymentStatus(1L, PaymentStatus.REFUNDED).getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"COMPLETED", "FAILED", "REFUNDED"})
    void updatePaymentStatus_fromTerminalToNonRefunded_throwsInvalidStateTransition(PaymentStatus current) {
        Payment payment = Payment.builder().id(1L).transactionId("TXN-1").status(current).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.updatePaymentStatus(1L, PaymentStatus.PROCESSING));

        assertEquals("INVALID_STATE_TRANSITION", exception.getErrorCode());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void updatePaymentStatus_missingPayment_throwsPaymentNotFound() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED));

        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void cancelPayment_missingPayment_throwsPaymentNotFound() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class, () -> paymentService.cancelPayment(1L));

        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void cancelPayment_pendingPayment_recordsCancellationReason() {
        Payment payment = Payment.builder().id(1L).transactionId("TXN-1").status(PaymentStatus.PENDING).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        paymentService.cancelPayment(1L);

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        assertEquals("Cancelled by user", payment.getFailureReason());
    }
}
