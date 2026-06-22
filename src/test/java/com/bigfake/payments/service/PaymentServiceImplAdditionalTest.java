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

    @Test
    void processPayment_wireTransfer_success() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("500.00"))
                .currency("USD")
                .paymentType(PaymentType.WIRE)
                .customerName("John Doe")
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });

        PaymentResponse response = paymentService.processPayment(request);

        assertNotNull(response);
    }

    @Test
    void processPayment_wireTransfer_belowMinimum() {
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
    void processPayment_wireTransfer_missingCustomerName() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("500.00"))
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
    void processPayment_wireTransfer_emptyCustomerName() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("500.00"))
                .currency("USD")
                .paymentType(PaymentType.WIRE)
                .customerName("   ")
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("WIRE_NAME_REQUIRED", ex.getErrorCode());
    }

    @Test
    void processPayment_ach_dailyLimitExceeded() {
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

    @Test
    void processPayment_ach_withinLimit() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("5000.00"))
                .currency("USD")
                .paymentType(PaymentType.ACH)
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(new BigDecimal("1000.00"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });

        PaymentResponse response = paymentService.processPayment(request);

        assertNotNull(response);
    }

    @Test
    void processPayment_creditCard_missingCardInfo() {
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
    void processPayment_creditCard_invalidCardLength() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("123")
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("CARD_INFO_REQUIRED", ex.getErrorCode());
    }

    @Test
    void processPayment_debit_missingCardInfo() {
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
    void processPayment_nonUsdCurrency_conversion() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("EUR")
                .paymentType(PaymentType.WIRE)
                .customerName("Test User")
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(currencyConverter.convertToUsd(new BigDecimal("100.00"), "EUR"))
                .thenReturn(new BigDecimal("108.75"));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });

        PaymentResponse response = paymentService.processPayment(request);

        assertNotNull(response);
        verify(currencyConverter).convertToUsd(new BigDecimal("100.00"), "EUR");
    }

    @Test
    void processPayment_unsupportedCurrency() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("XYZ")
                .paymentType(PaymentType.WIRE)
                .customerName("Test User")
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(currencyConverter.convertToUsd(new BigDecimal("100.00"), "XYZ")).thenReturn(null);

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("UNSUPPORTED_CURRENCY", ex.getErrorCode());
    }

    @Test
    void processPayment_merchantDailyLimitExceeded() {
        testMerchant.setDailyLimit(new BigDecimal("1000.00"));
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("500.00"))
                .currency("USD")
                .paymentType(PaymentType.WIRE)
                .customerName("John Doe")
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(new BigDecimal("800.00"));

        assertThrows(InsufficientFundsException.class,
                () -> paymentService.processPayment(request));
    }

    @Test
    void processPayment_merchantNoDailyLimit() {
        testMerchant.setDailyLimit(null);
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("500.00"))
                .currency("USD")
                .paymentType(PaymentType.WIRE)
                .customerName("John Doe")
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });

        PaymentResponse response = paymentService.processPayment(request);

        assertNotNull(response);
    }

    @Test
    void processPayment_velocityExceeded() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        // Return 10 payments in the last minute to trigger velocity check
        List<Payment> recentPayments = Collections.nCopies(10, Payment.builder().build());
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(recentPayments);

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("VELOCITY_EXCEEDED", ex.getErrorCode());
    }

    @Test
    void processPayment_notificationFailure_doesNotAffectPayment() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("500.00"))
                .currency("USD")
                .paymentType(PaymentType.WIRE)
                .customerName("John Doe")
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });
        doThrow(new RuntimeException("Notification error"))
                .when(notificationService).sendPaymentNotification(any());

        PaymentResponse response = paymentService.processPayment(request);

        assertNotNull(response);
    }

    @Test
    void getPaymentByTransactionId_success() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-TEST")
                .amount(new BigDecimal("100.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        when(paymentRepository.findByTransactionId("TXN-TEST")).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentByTransactionId("TXN-TEST");

        assertEquals("TXN-TEST", response.getTransactionId());
    }

    @Test
    void getPaymentByTransactionId_notFound() {
        when(paymentRepository.findByTransactionId("TXN-MISSING")).thenReturn(Optional.empty());

        assertThrows(PaymentException.class,
                () -> paymentService.getPaymentByTransactionId("TXN-MISSING"));
    }

    @Test
    void getPaymentsByMerchant_returnsList() {
        Payment p1 = Payment.builder().id(1L).transactionId("TXN-1").build();
        Payment p2 = Payment.builder().id(2L).transactionId("TXN-2").build();
        when(paymentRepository.findByMerchantIdAndStatus(1L, null)).thenReturn(List.of(p1, p2));

        List<PaymentResponse> responses = paymentService.getPaymentsByMerchant(1L);

        assertEquals(2, responses.size());
    }

    @Test
    void getPaymentsByMerchant_emptyList() {
        when(paymentRepository.findByMerchantIdAndStatus(999L, null)).thenReturn(Collections.emptyList());

        List<PaymentResponse> responses = paymentService.getPaymentsByMerchant(999L);

        assertTrue(responses.isEmpty());
    }

    @Test
    void updatePaymentStatus_success() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-UPD")
                .status(PaymentStatus.PROCESSING)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        PaymentResponse response = paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED);

        assertNotNull(response);
        verify(paymentRepository).save(argThat(p -> p.getCompletedAt() != null));
    }

    @Test
    void updatePaymentStatus_terminalStateBlocked() {
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
                .transactionId("TXN-REF")
                .status(PaymentStatus.COMPLETED)
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
    void processPayment_nullDailyTotal_treatedAsZero() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("500.00"))
                .currency("USD")
                .paymentType(PaymentType.WIRE)
                .customerName("John Doe")
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(null);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });

        PaymentResponse response = paymentService.processPayment(request);

        assertNotNull(response);
    }
}
