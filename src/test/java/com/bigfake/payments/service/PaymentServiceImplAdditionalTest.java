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
import static org.mockito.Mockito.lenient;

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

    private PaymentRequest.PaymentRequestBuilder baseRequest() {
        return PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("99.99"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .customerEmail("test@example.com");
    }

    private void setupSuccessfulPaymentPath() {
        lenient().when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        lenient().when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(BigDecimal.ZERO);
        lenient().when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });
    }

    // --- Currency conversion ---

    @Test
    void processPayment_foreignCurrency_convertsToUsd() {
        PaymentRequest request = baseRequest()
                .currency("EUR")
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(currencyConverter.convertToUsd(any(BigDecimal.class), eq("EUR")))
                .thenReturn(new BigDecimal("108.75"));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });

        PaymentResponse response = paymentService.processPayment(request);
        assertNotNull(response);
        verify(currencyConverter).convertToUsd(any(BigDecimal.class), eq("EUR"));
    }

    @Test
    void processPayment_unsupportedCurrency_throws() {
        PaymentRequest request = baseRequest()
                .currency("XYZ")
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(currencyConverter.convertToUsd(any(BigDecimal.class), eq("XYZ")))
                .thenReturn(null);

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("UNSUPPORTED_CURRENCY", exception.getErrorCode());
    }

    // --- Wire transfer ---

    @Test
    void processPayment_wireTransfer_belowMinimum_throws() {
        PaymentRequest request = baseRequest()
                .paymentType(PaymentType.WIRE)
                .amount(new BigDecimal("50.00"))
                .cardLastFour(null)
                .customerName("John Doe")
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("WIRE_MINIMUM_NOT_MET", exception.getErrorCode());
    }

    @Test
    void processPayment_wireTransfer_missingCustomerName_throws() {
        PaymentRequest request = baseRequest()
                .paymentType(PaymentType.WIRE)
                .amount(new BigDecimal("500.00"))
                .cardLastFour(null)
                .customerName(null)
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("WIRE_NAME_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_wireTransfer_emptyCustomerName_throws() {
        PaymentRequest request = baseRequest()
                .paymentType(PaymentType.WIRE)
                .amount(new BigDecimal("500.00"))
                .cardLastFour(null)
                .customerName("  ")
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("WIRE_NAME_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_wireTransfer_success() {
        PaymentRequest request = baseRequest()
                .paymentType(PaymentType.WIRE)
                .amount(new BigDecimal("500.00"))
                .cardLastFour(null)
                .customerName("John Doe")
                .build();
        setupSuccessfulPaymentPath();

        PaymentResponse response = paymentService.processPayment(request);
        assertNotNull(response);
    }

    // --- ACH ---

    @Test
    void processPayment_ach_exceedsDailyLimit_throws() {
        PaymentRequest request = baseRequest()
                .paymentType(PaymentType.ACH)
                .amount(new BigDecimal("20000.00"))
                .cardLastFour(null)
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(new BigDecimal("10000.00"));

        assertThrows(InsufficientFundsException.class,
                () -> paymentService.processPayment(request));
    }

    @Test
    void processPayment_ach_withinDailyLimit_succeeds() {
        PaymentRequest request = baseRequest()
                .paymentType(PaymentType.ACH)
                .amount(new BigDecimal("1000.00"))
                .cardLastFour(null)
                .build();
        setupSuccessfulPaymentPath();

        PaymentResponse response = paymentService.processPayment(request);
        assertNotNull(response);
    }

    // --- Credit card ---

    @Test
    void processPayment_creditCard_missingCardLastFour_throws() {
        PaymentRequest request = baseRequest()
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour(null)
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("CARD_INFO_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_creditCard_wrongLengthCardLastFour_throws() {
        PaymentRequest request = baseRequest()
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("123")
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("CARD_INFO_REQUIRED", exception.getErrorCode());
    }

    // --- Debit card ---

    @Test
    void processPayment_debit_missingCardInfo_throws() {
        PaymentRequest request = baseRequest()
                .paymentType(PaymentType.DEBIT)
                .cardLastFour(null)
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("CARD_INFO_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_debit_success() {
        PaymentRequest request = baseRequest()
                .paymentType(PaymentType.DEBIT)
                .cardLastFour("5678")
                .build();
        setupSuccessfulPaymentPath();

        PaymentResponse response = paymentService.processPayment(request);
        assertNotNull(response);
    }

    // --- Velocity check ---

    @Test
    void processPayment_creditCard_velocityExceeded_throws() {
        PaymentRequest request = baseRequest()
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        // Return 10+ recent payments to trigger velocity check
        List<Payment> manyPayments = Collections.nCopies(11, Payment.builder().build());
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(manyPayments);

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("VELOCITY_EXCEEDED", exception.getErrorCode());
    }

    // --- Merchant daily limit ---

    @Test
    void processPayment_merchantDailyLimitExceeded_throws() {
        testMerchant.setDailyLimit(new BigDecimal("100.00"));
        PaymentRequest request = baseRequest()
                .amount(new BigDecimal("50.00"))
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(new BigDecimal("80.00"));
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());

        assertThrows(InsufficientFundsException.class,
                () -> paymentService.processPayment(request));
    }

    @Test
    void processPayment_merchantNoDailyLimit_skipsCheck() {
        testMerchant.setDailyLimit(null);
        PaymentRequest request = baseRequest().build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });

        PaymentResponse response = paymentService.processPayment(request);
        assertNotNull(response);
    }

    // --- Notification failure ---

    @Test
    void processPayment_notificationFailure_doesNotFail() {
        setupSuccessfulPaymentPath();
        doThrow(new RuntimeException("Notification failed"))
                .when(notificationService).sendPaymentNotification(any());

        PaymentRequest request = baseRequest().build();
        PaymentResponse response = paymentService.processPayment(request);
        assertNotNull(response);
    }

    // --- getPaymentByTransactionId ---

    @Test
    void getPaymentByTransactionId_found() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-ABC")
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();
        when(paymentRepository.findByTransactionId("TXN-ABC")).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentByTransactionId("TXN-ABC");
        assertEquals("TXN-ABC", response.getTransactionId());
    }

    @Test
    void getPaymentByTransactionId_notFound() {
        when(paymentRepository.findByTransactionId("NONEXISTENT")).thenReturn(Optional.empty());

        assertThrows(PaymentException.class,
                () -> paymentService.getPaymentByTransactionId("NONEXISTENT"));
    }

    // --- getPaymentsByMerchant ---

    @Test
    void getPaymentsByMerchant_returnsListOfResponses() {
        Payment p1 = Payment.builder().id(1L).transactionId("TXN-1").merchantId(1L)
                .amount(new BigDecimal("100")).currency("USD").status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD).build();
        Payment p2 = Payment.builder().id(2L).transactionId("TXN-2").merchantId(1L)
                .amount(new BigDecimal("200")).currency("USD").status(PaymentStatus.PENDING)
                .paymentType(PaymentType.ACH).build();
        when(paymentRepository.findByMerchantIdAndStatus(1L, null)).thenReturn(List.of(p1, p2));

        List<PaymentResponse> result = paymentService.getPaymentsByMerchant(1L);
        assertEquals(2, result.size());
    }

    @Test
    void getPaymentsByMerchant_empty_returnsEmptyList() {
        when(paymentRepository.findByMerchantIdAndStatus(1L, null)).thenReturn(Collections.emptyList());

        List<PaymentResponse> result = paymentService.getPaymentsByMerchant(1L);
        assertTrue(result.isEmpty());
    }

    // --- updatePaymentStatus ---

    @Test
    void updatePaymentStatus_success() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-UPD")
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .status(PaymentStatus.PROCESSING)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED);
        assertEquals(PaymentStatus.COMPLETED, response.getStatus());
    }

    @Test
    void updatePaymentStatus_completedToRefunded_allowed() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-REF")
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentService.updatePaymentStatus(1L, PaymentStatus.REFUNDED);
        assertEquals(PaymentStatus.REFUNDED, response.getStatus());
    }

    @Test
    void updatePaymentStatus_terminalToNonRefunded_throws() {
        Payment payment = Payment.builder()
                .id(1L)
                .status(PaymentStatus.COMPLETED)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.updatePaymentStatus(1L, PaymentStatus.PROCESSING));
        assertEquals("INVALID_STATE_TRANSITION", exception.getErrorCode());
    }

    @Test
    void updatePaymentStatus_notFound_throws() {
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(PaymentException.class,
                () -> paymentService.updatePaymentStatus(999L, PaymentStatus.COMPLETED));
    }

    // --- cancelPayment additional ---

    @Test
    void cancelPayment_notFound_throws() {
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(PaymentException.class,
                () -> paymentService.cancelPayment(999L));
    }

    // --- Idempotency with empty key ---

    @Test
    void processPayment_emptyIdempotencyKey_processesNormally() {
        PaymentRequest request = baseRequest()
                .idempotencyKey("")
                .build();
        setupSuccessfulPaymentPath();

        PaymentResponse response = paymentService.processPayment(request);
        assertNotNull(response);
        verify(paymentRepository, never()).findByIdempotencyKey(anyString());
    }

    @Test
    void processPayment_nullIdempotencyKey_processesNormally() {
        PaymentRequest request = baseRequest()
                .idempotencyKey(null)
                .build();
        setupSuccessfulPaymentPath();

        PaymentResponse response = paymentService.processPayment(request);
        assertNotNull(response);
    }

    // --- getTodaysTotalForMerchant returns null ---

    @Test
    void processPayment_nullDailyTotal_treatedAsZero() {
        PaymentRequest request = baseRequest().build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(null);
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });

        PaymentResponse response = paymentService.processPayment(request);
        assertNotNull(response);
    }
}
