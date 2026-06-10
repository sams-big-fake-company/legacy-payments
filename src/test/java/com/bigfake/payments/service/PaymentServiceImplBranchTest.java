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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Branch coverage tests for PaymentServiceImpl covering payment-type specific
 * validation, currency conversion, limits, status updates, and lookups.
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
                .build();
    }

    private PaymentRequest.PaymentRequestBuilder baseRequest() {
        return PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("200.00"))
                .currency("USD");
    }

    private void stubSave() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });
    }

    @Test
    void processPayment_unsupportedCurrency_throws() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        when(currencyConverter.convertToUsd(any(), eq("ZWL"))).thenReturn(null);

        PaymentRequest request = baseRequest()
                .currency("ZWL")
                .paymentType(PaymentType.WIRE)
                .customerName("Alice")
                .build();

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("UNSUPPORTED_CURRENCY", ex.getErrorCode());
    }

    @Test
    void processPayment_foreignCurrency_convertsForLimitChecks() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        when(currencyConverter.convertToUsd(new BigDecimal("200.00"), "EUR"))
                .thenReturn(new BigDecimal("217.50"));
        stubSave();

        PaymentRequest request = baseRequest()
                .currency("EUR")
                .paymentType(PaymentType.WIRE)
                .customerName("Alice")
                .build();

        PaymentResponse response = paymentService.processPayment(request);

        assertNotNull(response);
        verify(currencyConverter).convertToUsd(new BigDecimal("200.00"), "EUR");
    }

    @Test
    void processPayment_wireBelowMinimum_throws() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        PaymentRequest request = baseRequest()
                .amount(new BigDecimal("99.99"))
                .paymentType(PaymentType.WIRE)
                .customerName("Alice")
                .build();

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("WIRE_MINIMUM_NOT_MET", ex.getErrorCode());
    }

    @Test
    void processPayment_wireMissingCustomerName_throws() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        PaymentRequest request = baseRequest()
                .paymentType(PaymentType.WIRE)
                .customerName("   ")
                .build();

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("WIRE_NAME_REQUIRED", ex.getErrorCode());
    }

    @Test
    void processPayment_wireSuccess_appliesWireFee() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        stubSave();

        PaymentRequest request = baseRequest()
                .paymentType(PaymentType.WIRE)
                .customerName("Alice")
                .build();

        PaymentResponse response = paymentService.processPayment(request);

        // 200.00 * 0.001 = 0.2000
        assertEquals(new BigDecimal("0.2000"), response.getFeeAmount());
        assertEquals(new BigDecimal("199.8000"), response.getNetAmount());
    }

    @Test
    void processPayment_achExceedsDailyLimit_throwsInsufficientFunds() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(new BigDecimal("24900.00"));

        PaymentRequest request = baseRequest()
                .paymentType(PaymentType.ACH)
                .build();

        InsufficientFundsException ex = assertThrows(InsufficientFundsException.class,
                () -> paymentService.processPayment(request));
        assertEquals("INSUFFICIENT_FUNDS", ex.getErrorCode());
        assertEquals(new BigDecimal("200.00"), ex.getRequestedAmount());
        assertEquals(new BigDecimal("100.00"), ex.getAvailableAmount());
    }

    @Test
    void processPayment_achWithinLimit_succeeds() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(null); // null total treated as zero
        stubSave();

        PaymentRequest request = baseRequest()
                .paymentType(PaymentType.ACH)
                .build();

        PaymentResponse response = paymentService.processPayment(request);

        assertNotNull(response.getTransactionId());
    }

    @Test
    void processPayment_debitMissingCardInfo_throws() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        PaymentRequest request = baseRequest()
                .paymentType(PaymentType.DEBIT)
                .build();

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("CARD_INFO_REQUIRED", ex.getErrorCode());
    }

    @Test
    void processPayment_creditCardMissingCardInfo_throws() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        PaymentRequest request = baseRequest()
                .paymentType(PaymentType.CREDIT_CARD)
                .build();

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("CARD_INFO_REQUIRED", ex.getErrorCode());
    }

    @Test
    void processPayment_velocityExceeded_throws() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        List<Payment> tenRecent = Collections.nCopies(10, Payment.builder().build());
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(tenRecent);

        PaymentRequest request = baseRequest()
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .build();

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("VELOCITY_EXCEEDED", ex.getErrorCode());
    }

    @Test
    void processPayment_merchantDailyLimitExceeded_throwsInsufficientFunds() {
        merchant.setDailyLimit(new BigDecimal("500.00"));
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(new BigDecimal("400.00"));

        PaymentRequest request = baseRequest()
                .paymentType(PaymentType.WIRE)
                .customerName("Alice")
                .build();

        InsufficientFundsException ex = assertThrows(InsufficientFundsException.class,
                () -> paymentService.processPayment(request));
        assertEquals(new BigDecimal("100.00"), ex.getAvailableAmount());
    }

    @Test
    void processPayment_notificationFailure_doesNotFailPayment() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        stubSave();
        doThrow(new RuntimeException("smtp down"))
                .when(notificationService).sendPaymentNotification(any(Payment.class));

        PaymentRequest request = baseRequest()
                .paymentType(PaymentType.WIRE)
                .customerName("Alice")
                .build();

        PaymentResponse response = paymentService.processPayment(request);

        assertNotNull(response);
    }

    @Test
    void getPaymentByTransactionId_found_returnsResponse() {
        Payment payment = Payment.builder()
                .id(3L)
                .transactionId("TXN-LOOKUP1")
                .status(PaymentStatus.COMPLETED)
                .build();
        when(paymentRepository.findByTransactionId("TXN-LOOKUP1")).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentByTransactionId("TXN-LOOKUP1");

        assertEquals("TXN-LOOKUP1", response.getTransactionId());
        assertEquals(3L, response.getId());
    }

    @Test
    void getPaymentByTransactionId_notFound_throws() {
        when(paymentRepository.findByTransactionId("TXN-MISSING")).thenReturn(Optional.empty());

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.getPaymentByTransactionId("TXN-MISSING"));
        assertEquals("PAYMENT_NOT_FOUND", ex.getErrorCode());
    }

    @Test
    void getPaymentsByMerchant_mapsAllPayments() {
        Payment payment = Payment.builder()
                .id(4L)
                .transactionId("TXN-LIST1")
                .status(PaymentStatus.COMPLETED)
                .build();
        when(paymentRepository.findByMerchantIdAndStatus(1L, null)).thenReturn(List.of(payment));

        List<PaymentResponse> responses = paymentService.getPaymentsByMerchant(1L);

        assertEquals(1, responses.size());
        assertEquals("TXN-LIST1", responses.get(0).getTransactionId());
    }

    @Test
    void updatePaymentStatus_fromPending_succeeds() {
        Payment payment = Payment.builder()
                .id(5L)
                .transactionId("TXN-UPD1")
                .status(PaymentStatus.PENDING)
                .build();
        when(paymentRepository.findById(5L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = paymentService.updatePaymentStatus(5L, PaymentStatus.COMPLETED);

        assertEquals(PaymentStatus.COMPLETED, response.getStatus());
        assertNotNull(response.getCompletedAt());
    }

    @Test
    void updatePaymentStatus_terminalToRefunded_isAllowed() {
        Payment payment = Payment.builder()
                .id(6L)
                .transactionId("TXN-UPD2")
                .status(PaymentStatus.COMPLETED)
                .build();
        when(paymentRepository.findById(6L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = paymentService.updatePaymentStatus(6L, PaymentStatus.REFUNDED);

        assertEquals(PaymentStatus.REFUNDED, response.getStatus());
    }

    @Test
    void updatePaymentStatus_terminalToOther_throws() {
        Payment payment = Payment.builder()
                .id(7L)
                .status(PaymentStatus.FAILED)
                .build();
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(payment));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.updatePaymentStatus(7L, PaymentStatus.PROCESSING));
        assertEquals("INVALID_STATE_TRANSITION", ex.getErrorCode());
    }

    @Test
    void updatePaymentStatus_notFound_throws() {
        when(paymentRepository.findById(99L)).thenReturn(Optional.empty());

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.updatePaymentStatus(99L, PaymentStatus.COMPLETED));
        assertEquals("PAYMENT_NOT_FOUND", ex.getErrorCode());
    }

    @Test
    void cancelPayment_notFound_throws() {
        when(paymentRepository.findById(99L)).thenReturn(Optional.empty());

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.cancelPayment(99L));
        assertEquals("PAYMENT_NOT_FOUND", ex.getErrorCode());
    }

    @Test
    void getPaymentById_found_mapsAllFields() {
        Payment payment = Payment.builder()
                .id(8L)
                .transactionId("TXN-MAP1")
                .merchantId(1L)
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.DEBIT)
                .feeAmount(new BigDecimal("0.15"))
                .netAmount(new BigDecimal("9.85"))
                .gatewayReference("GW-ABC")
                .build();
        when(paymentRepository.findById(8L)).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentById(8L);

        assertEquals("TXN-MAP1", response.getTransactionId());
        assertEquals(new BigDecimal("0.15"), response.getFeeAmount());
        assertEquals(new BigDecimal("9.85"), response.getNetAmount());
        assertEquals("GW-ABC", response.getGatewayReference());
        assertEquals(PaymentType.DEBIT, response.getPaymentType());
    }
}
