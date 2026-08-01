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
import java.time.LocalDateTime;
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

    private PaymentRequest buildRequest(PaymentType type, String amount) {
        return PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal(amount))
                .currency("USD")
                .paymentType(type)
                .cardLastFour("4242")
                .customerEmail("test@example.com")
                .customerName("Test User")
                .build();
    }

    private void setupSuccessfulPaymentMocks() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            if (p.getId() == null) p.setId(1L);
            return p;
        });
    }

    @Test
    void processPayment_wireTransfer_success() {
        PaymentRequest request = buildRequest(PaymentType.WIRE, "500.00");
        request.setCardLastFour(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            if (p.getId() == null) p.setId(1L);
            return p;
        });

        PaymentResponse response = paymentService.processPayment(request);

        assertNotNull(response);
        assertNotNull(response.getTransactionId());
    }

    @Test
    void processPayment_wireTransfer_belowMinimum() {
        PaymentRequest request = buildRequest(PaymentType.WIRE, "50.00");
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("WIRE_MINIMUM_NOT_MET", exception.getErrorCode());
    }

    @Test
    void processPayment_wireTransfer_missingCustomerName() {
        PaymentRequest request = buildRequest(PaymentType.WIRE, "500.00");
        request.setCustomerName(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("WIRE_NAME_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_wireTransfer_emptyCustomerName() {
        PaymentRequest request = buildRequest(PaymentType.WIRE, "500.00");
        request.setCustomerName("   ");
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("WIRE_NAME_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_achPayment_success() {
        PaymentRequest request = buildRequest(PaymentType.ACH, "1000.00");
        request.setCardLastFour(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            if (p.getId() == null) p.setId(1L);
            return p;
        });

        PaymentResponse response = paymentService.processPayment(request);

        assertNotNull(response);
    }

    @Test
    void processPayment_achPayment_dailyLimitExceeded() {
        PaymentRequest request = buildRequest(PaymentType.ACH, "1000.00");
        request.setCardLastFour(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(new BigDecimal("24500.00"));

        assertThrows(InsufficientFundsException.class,
                () -> paymentService.processPayment(request));
    }

    @Test
    void processPayment_creditCard_missingCardLastFour() {
        PaymentRequest request = buildRequest(PaymentType.CREDIT_CARD, "99.99");
        request.setCardLastFour(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("CARD_INFO_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_creditCard_invalidCardLastFour() {
        PaymentRequest request = buildRequest(PaymentType.CREDIT_CARD, "99.99");
        request.setCardLastFour("42");
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("CARD_INFO_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_debitCard_missingCardLastFour() {
        PaymentRequest request = buildRequest(PaymentType.DEBIT, "99.99");
        request.setCardLastFour(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("CARD_INFO_REQUIRED", exception.getErrorCode());
    }

    @Test
    void processPayment_nonUsdCurrency_conversion() {
        PaymentRequest request = buildRequest(PaymentType.CREDIT_CARD, "100.00");
        request.setCurrency("EUR");
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(currencyConverter.convertToUsd(new BigDecimal("100.00"), "EUR"))
                .thenReturn(new BigDecimal("108.75"));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            if (p.getId() == null) p.setId(1L);
            return p;
        });

        PaymentResponse response = paymentService.processPayment(request);

        assertNotNull(response);
        verify(currencyConverter).convertToUsd(new BigDecimal("100.00"), "EUR");
    }

    @Test
    void processPayment_unsupportedCurrency() {
        PaymentRequest request = buildRequest(PaymentType.CREDIT_CARD, "100.00");
        request.setCurrency("XYZ");
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(currencyConverter.convertToUsd(new BigDecimal("100.00"), "XYZ")).thenReturn(null);

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("UNSUPPORTED_CURRENCY", exception.getErrorCode());
    }

    @Test
    void processPayment_merchantDailyLimitExceeded() {
        testMerchant.setDailyLimit(new BigDecimal("1000.00"));
        PaymentRequest request = buildRequest(PaymentType.CREDIT_CARD, "200.00");
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(new BigDecimal("900.00"));
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());

        assertThrows(InsufficientFundsException.class,
                () -> paymentService.processPayment(request));
    }

    @Test
    void processPayment_merchantNoDailyLimit() {
        testMerchant.setDailyLimit(null);
        PaymentRequest request = buildRequest(PaymentType.CREDIT_CARD, "200.00");
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            if (p.getId() == null) p.setId(1L);
            return p;
        });

        PaymentResponse response = paymentService.processPayment(request);

        assertNotNull(response);
    }

    @Test
    void processPayment_notificationFailure() {
        PaymentRequest request = buildRequest(PaymentType.CREDIT_CARD, "99.99");
        setupSuccessfulPaymentMocks();
        doThrow(new RuntimeException("Notification error"))
                .when(notificationService).sendPaymentNotification(any());

        PaymentResponse response = paymentService.processPayment(request);

        assertNotNull(response);
    }

    @Test
    void processPayment_emptyIdempotencyKey() {
        PaymentRequest request = buildRequest(PaymentType.CREDIT_CARD, "99.99");
        request.setIdempotencyKey("");
        setupSuccessfulPaymentMocks();

        PaymentResponse response = paymentService.processPayment(request);

        assertNotNull(response);
        verify(paymentRepository, never()).findByIdempotencyKey(anyString());
    }

    @Test
    void processPayment_nullCurrency_treatedAsUsd() {
        PaymentRequest request = buildRequest(PaymentType.CREDIT_CARD, "99.99");
        request.setCurrency(null);
        setupSuccessfulPaymentMocks();

        PaymentResponse response = paymentService.processPayment(request);

        assertNotNull(response);
    }

    @Test
    void getPaymentByTransactionId_success() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-LOOKUP001")
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();
        when(paymentRepository.findByTransactionId("TXN-LOOKUP001"))
                .thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentByTransactionId("TXN-LOOKUP001");

        assertNotNull(response);
        assertEquals("TXN-LOOKUP001", response.getTransactionId());
    }

    @Test
    void getPaymentByTransactionId_notFound() {
        when(paymentRepository.findByTransactionId("TXN-MISSING"))
                .thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.getPaymentByTransactionId("TXN-MISSING"));
        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void getPaymentsByMerchant_returnsList() {
        Payment p1 = Payment.builder().id(1L).transactionId("TXN-1").merchantId(1L)
                .amount(new BigDecimal("10.00")).currency("USD")
                .status(PaymentStatus.COMPLETED).paymentType(PaymentType.CREDIT_CARD).build();
        Payment p2 = Payment.builder().id(2L).transactionId("TXN-2").merchantId(1L)
                .amount(new BigDecimal("20.00")).currency("USD")
                .status(PaymentStatus.PENDING).paymentType(PaymentType.ACH).build();
        when(paymentRepository.findByMerchantIdAndStatus(1L, null))
                .thenReturn(List.of(p1, p2));

        List<PaymentResponse> results = paymentService.getPaymentsByMerchant(1L);

        assertEquals(2, results.size());
    }

    @Test
    void getPaymentsByMerchant_emptyList() {
        when(paymentRepository.findByMerchantIdAndStatus(1L, null))
                .thenReturn(Collections.emptyList());

        List<PaymentResponse> results = paymentService.getPaymentsByMerchant(1L);

        assertTrue(results.isEmpty());
    }

    @Test
    void updatePaymentStatus_success() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-STATUS001")
                .status(PaymentStatus.PROCESSING)
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
    void updatePaymentStatus_toNonCompletedDoesNotSetCompletedAt() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-STATUS002")
                .status(PaymentStatus.PENDING)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        paymentService.updatePaymentStatus(1L, PaymentStatus.PROCESSING);

        verify(paymentRepository).save(argThat(p -> p.getCompletedAt() == null));
    }

    @Test
    void updatePaymentStatus_fromTerminalState_throwsException() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-STATUS003")
                .status(PaymentStatus.COMPLETED)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.updatePaymentStatus(1L, PaymentStatus.PROCESSING));
        assertEquals("INVALID_STATE_TRANSITION", exception.getErrorCode());
    }

    @Test
    void updatePaymentStatus_fromTerminalToRefunded_allowed() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-STATUS004")
                .status(PaymentStatus.COMPLETED)
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
    void updatePaymentStatus_notFound() {
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(PaymentException.class,
                () -> paymentService.updatePaymentStatus(999L, PaymentStatus.COMPLETED));
    }

    @Test
    void cancelPayment_notFound() {
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(PaymentException.class,
                () -> paymentService.cancelPayment(999L));
    }

    @Test
    void processPayment_creditCard_velocityExceeded() {
        PaymentRequest request = buildRequest(PaymentType.CREDIT_CARD, "99.99");
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        List<Payment> manyPayments = Collections.nCopies(10, Payment.builder().id(1L).build());
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(manyPayments);

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("VELOCITY_EXCEEDED", exception.getErrorCode());
    }

    @Test
    void processPayment_todaysTotalNull_treatedAsZero() {
        PaymentRequest request = buildRequest(PaymentType.CREDIT_CARD, "99.99");
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(null);
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            if (p.getId() == null) p.setId(1L);
            return p;
        });

        PaymentResponse response = paymentService.processPayment(request);

        assertNotNull(response);
    }
}
