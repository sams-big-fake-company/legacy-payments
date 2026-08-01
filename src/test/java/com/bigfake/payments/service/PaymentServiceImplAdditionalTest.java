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
    void processPayment_wireTransfer_missingName() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("200.00"))
                .currency("USD")
                .paymentType(PaymentType.WIRE)
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("WIRE_NAME_REQUIRED", ex.getErrorCode());
    }

    @Test
    void processPayment_wireTransfer_emptyName() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("200.00"))
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
    void processPayment_ach_exceedsDailyLimit() {
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
    void processPayment_creditCard_missingCardInfo() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("CARD_INFO_REQUIRED", ex.getErrorCode());
    }

    @Test
    void processPayment_creditCard_invalidCardLastFour() {
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
    void processPayment_debit_missingCardInfo() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentType(PaymentType.DEBIT)
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("CARD_INFO_REQUIRED", ex.getErrorCode());
    }

    @Test
    void processPayment_currencyConversion_nonUsd() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("EUR")
                .paymentType(PaymentType.WIRE)
                .customerName("John Doe")
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
    }

    @Test
    void processPayment_unsupportedCurrency() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("XYZ")
                .paymentType(PaymentType.WIRE)
                .customerName("John Doe")
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(currencyConverter.convertToUsd(new BigDecimal("100.00"), "XYZ"))
                .thenReturn(null);

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("UNSUPPORTED_CURRENCY", ex.getErrorCode());
    }

    @Test
    void processPayment_merchantDailyLimitExceeded() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50000.00"))
                .currency("USD")
                .paymentType(PaymentType.WIRE)
                .customerName("John Doe")
                .build();

        testMerchant.setDailyLimit(new BigDecimal("60000.00"));
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(new BigDecimal("20000.00"));

        assertThrows(InsufficientFundsException.class,
                () -> paymentService.processPayment(request));
    }

    @Test
    void processPayment_merchantNoDailyLimit_noException() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("1000.00"))
                .currency("USD")
                .paymentType(PaymentType.WIRE)
                .customerName("John Doe")
                .build();

        testMerchant.setDailyLimit(null);
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
        // Create 10+ recent payments to trigger velocity check
        List<Payment> manyPayments = Collections.nCopies(10, Payment.builder().id(1L).build());
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(manyPayments);

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(request));
        assertEquals("VELOCITY_EXCEEDED", ex.getErrorCode());
    }

    @Test
    void processPayment_notificationFailure_doesNotAffectPayment() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("99.99"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });
        doThrow(new RuntimeException("Notification service down"))
                .when(notificationService).sendPaymentNotification(any(Payment.class));

        PaymentResponse response = paymentService.processPayment(request);
        assertNotNull(response);
    }

    @Test
    void getPaymentByTransactionId_success() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-LOOKUP001")
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
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

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.getPaymentByTransactionId("TXN-MISSING"));
        assertEquals("PAYMENT_NOT_FOUND", ex.getErrorCode());
    }

    @Test
    void getPaymentsByMerchant_returnsList() {
        Payment payment1 = Payment.builder()
                .id(1L)
                .transactionId("TXN-M001")
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();
        Payment payment2 = Payment.builder()
                .id(2L)
                .transactionId("TXN-M002")
                .merchantId(1L)
                .amount(new BigDecimal("75.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.WIRE)
                .build();

        when(paymentRepository.findByMerchantIdAndStatus(1L, null))
                .thenReturn(List.of(payment1, payment2));

        List<PaymentResponse> results = paymentService.getPaymentsByMerchant(1L);
        assertEquals(2, results.size());
    }

    @Test
    void updatePaymentStatus_success() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-STATUS001")
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.PROCESSING)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();

        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED);

        assertNotNull(response);
        assertEquals(PaymentStatus.COMPLETED, response.getStatus());
        assertNotNull(response.getCompletedAt());
    }

    @Test
    void updatePaymentStatus_terminalToNonRefunded_throws() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-TERM001")
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();

        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.updatePaymentStatus(1L, PaymentStatus.PROCESSING));
        assertEquals("INVALID_STATE_TRANSITION", ex.getErrorCode());
    }

    @Test
    void updatePaymentStatus_terminalToRefunded_succeeds() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-REFUND001")
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();

        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentService.updatePaymentStatus(1L, PaymentStatus.REFUNDED);
        assertNotNull(response);
        assertEquals(PaymentStatus.REFUNDED, response.getStatus());
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
}
