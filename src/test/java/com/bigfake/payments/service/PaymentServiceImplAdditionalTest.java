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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplAdditionalTest {

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

    @BeforeEach
    void setUp() {
        testMerchant = Merchant.builder()
                .id(1L)
                .merchantCode("MERCH001")
                .businessName("Test Store")
                .isActive(true)
                .dailyLimit(new BigDecimal("100000.00"))
                .build();
    }

    // ---- Currency conversion path ----

    @Test
    void processPayment_nonUsdCurrency_convertsToUsd() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("EUR")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(currencyConverter.convertToUsd(new BigDecimal("100.00"), "EUR"))
                .thenReturn(new BigDecimal("108.75"));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0);
            p.setId(1L);
            return p;
        });

        PaymentResponse response = paymentService.processPayment(request);
        assertNotNull(response);
        verify(currencyConverter).convertToUsd(new BigDecimal("100.00"), "EUR");
    }

    @Test
    void processPayment_unsupportedCurrency_throwsException() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("XYZ")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(currencyConverter.convertToUsd(new BigDecimal("100.00"), "XYZ")).thenReturn(null);

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("UNSUPPORTED_CURRENCY", ex.getErrorCode());
    }

    // ---- Wire transfer path ----

    @Test
    void processPayment_wireTransfer_belowMinimum_throwsException() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentType(PaymentType.WIRE)
                .customerName("John Doe")
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("WIRE_MINIMUM_NOT_MET", ex.getErrorCode());
    }

    @Test
    void processPayment_wireTransfer_noCustomerName_throwsException() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("200.00"))
                .currency("USD")
                .paymentType(PaymentType.WIRE)
                .customerName(null)
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("WIRE_NAME_REQUIRED", ex.getErrorCode());
    }

    @Test
    void processPayment_wireTransfer_emptyCustomerName_throwsException() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("200.00"))
                .currency("USD")
                .paymentType(PaymentType.WIRE)
                .customerName("  ")
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("WIRE_NAME_REQUIRED", ex.getErrorCode());
    }

    @Test
    void processPayment_wireTransfer_success() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("200.00"))
                .currency("USD")
                .paymentType(PaymentType.WIRE)
                .customerName("John Doe")
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0);
            p.setId(1L);
            return p;
        });

        PaymentResponse response = paymentService.processPayment(request);
        assertNotNull(response);
    }

    // ---- ACH path ----

    @Test
    void processPayment_achDailyLimitExceeded_throwsInsufficientFunds() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("20000.00"))
                .currency("USD")
                .paymentType(PaymentType.ACH)
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(new BigDecimal("10000.00"));

        assertThrows(InsufficientFundsException.class,
                () -> paymentService.processPayment(request));
    }

    // ---- Credit card path ----

    @Test
    void processPayment_creditCard_missingCardLastFour_throwsException() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour(null)
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("CARD_INFO_REQUIRED", ex.getErrorCode());
    }

    @Test
    void processPayment_creditCard_invalidCardLastFour_throwsException() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("12")
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("CARD_INFO_REQUIRED", ex.getErrorCode());
    }

    @Test
    void processPayment_creditCard_velocityExceeded_throwsException() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(createPaymentList(10));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("VELOCITY_EXCEEDED", ex.getErrorCode());
    }

    // ---- Debit card path ----

    @Test
    void processPayment_debit_missingCardLastFour_throwsException() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentType(PaymentType.DEBIT)
                .cardLastFour(null)
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("CARD_INFO_REQUIRED", ex.getErrorCode());
    }

    @Test
    void processPayment_debit_success() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentType(PaymentType.DEBIT)
                .cardLastFour("1234")
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0);
            p.setId(1L);
            return p;
        });

        PaymentResponse response = paymentService.processPayment(request);
        assertNotNull(response);
    }

    // ---- Merchant daily limit ----

    @Test
    void processPayment_merchantDailyLimitExceeded_throwsInsufficientFunds() {
        testMerchant.setDailyLimit(new BigDecimal("1000.00"));

        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("500.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(new BigDecimal("600.00"));

        assertThrows(InsufficientFundsException.class,
                () -> paymentService.processPayment(request));
    }

    @Test
    void processPayment_merchantNullDailyLimit_noLimitCheck() {
        testMerchant.setDailyLimit(null);

        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0);
            p.setId(1L);
            return p;
        });

        PaymentResponse response = paymentService.processPayment(request);
        assertNotNull(response);
    }

    // ---- getPaymentByTransactionId ----

    @Test
    void getPaymentByTransactionId_found() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-TEST123")
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();
        when(paymentRepository.findByTransactionId("TXN-TEST123")).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentByTransactionId("TXN-TEST123");
        assertNotNull(response);
        assertEquals("TXN-TEST123", response.getTransactionId());
    }

    @Test
    void getPaymentByTransactionId_notFound_throwsException() {
        when(paymentRepository.findByTransactionId("TXN-UNKNOWN")).thenReturn(Optional.empty());

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.getPaymentByTransactionId("TXN-UNKNOWN"));
        assertEquals("PAYMENT_NOT_FOUND", ex.getErrorCode());
    }

    // ---- getPaymentsByMerchant ----

    @Test
    void getPaymentsByMerchant_returnsResponseList() {
        List<Payment> payments = List.of(
                Payment.builder().id(1L).transactionId("TXN-1").merchantId(1L)
                        .amount(new BigDecimal("10.00")).currency("USD")
                        .status(PaymentStatus.COMPLETED).paymentType(PaymentType.CREDIT_CARD).build(),
                Payment.builder().id(2L).transactionId("TXN-2").merchantId(1L)
                        .amount(new BigDecimal("20.00")).currency("USD")
                        .status(PaymentStatus.PENDING).paymentType(PaymentType.WIRE).build()
        );
        when(paymentRepository.findByMerchantIdAndStatus(1L, null)).thenReturn(payments);

        List<PaymentResponse> result = paymentService.getPaymentsByMerchant(1L);
        assertEquals(2, result.size());
    }

    @Test
    void getPaymentsByMerchant_emptyList() {
        when(paymentRepository.findByMerchantIdAndStatus(1L, null)).thenReturn(Collections.emptyList());

        List<PaymentResponse> result = paymentService.getPaymentsByMerchant(1L);
        assertTrue(result.isEmpty());
    }

    // ---- updatePaymentStatus ----

    @Test
    void updatePaymentStatus_validTransition_success() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-123")
                .status(PaymentStatus.PROCESSING)
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        PaymentResponse response = paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED);
        assertNotNull(response);
        verify(paymentRepository).save(argThat(p -> p.getCompletedAt() != null));
    }

    @Test
    void updatePaymentStatus_terminalToNonRefunded_throwsException() {
        Payment payment = Payment.builder()
                .id(1L)
                .status(PaymentStatus.COMPLETED)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.updatePaymentStatus(1L, PaymentStatus.PROCESSING));
        assertEquals("INVALID_STATE_TRANSITION", ex.getErrorCode());
    }

    @Test
    void updatePaymentStatus_terminalToRefunded_allowed() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-123")
                .status(PaymentStatus.COMPLETED)
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        PaymentResponse response = paymentService.updatePaymentStatus(1L, PaymentStatus.REFUNDED);
        assertNotNull(response);
    }

    @Test
    void updatePaymentStatus_paymentNotFound_throwsException() {
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(PaymentException.class,
                () -> paymentService.updatePaymentStatus(999L, PaymentStatus.COMPLETED));
    }

    // ---- cancelPayment: not found ----

    @Test
    void cancelPayment_paymentNotFound_throwsException() {
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(PaymentException.class,
                () -> paymentService.cancelPayment(999L));
    }

    // ---- Notification failure does not fail payment ----

    @Test
    void processPayment_notificationFailure_paymentStillSucceeds() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0);
            p.setId(1L);
            return p;
        });
        doThrow(new RuntimeException("notification failed")).when(notificationService)
                .sendPaymentNotification(any(Payment.class));

        PaymentResponse response = paymentService.processPayment(request);
        assertNotNull(response);
    }

    // ---- Idempotency with empty key ----

    @Test
    void processPayment_emptyIdempotencyKey_processesNormally() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .idempotencyKey("")
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0);
            p.setId(1L);
            return p;
        });

        PaymentResponse response = paymentService.processPayment(request);
        assertNotNull(response);
        verify(paymentRepository, never()).findByIdempotencyKey(anyString());
    }

    // ---- helper methods ----

    private List<Payment> createPaymentList(int size) {
        return java.util.stream.IntStream.range(0, size)
                .mapToObj(i -> Payment.builder()
                        .id((long) i)
                        .transactionId("TXN-" + i)
                        .build())
                .collect(java.util.stream.Collectors.toList());
    }
}
