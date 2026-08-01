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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
    private PaymentRequest validRequest;

    @BeforeEach
    void setUp() {
        testMerchant = Merchant.builder()
                .id(1L)
                .merchantCode("MERCH001")
                .businessName("Test Store")
                .isActive(true)
                .dailyLimit(new BigDecimal("100000.00"))
                .build();

        validRequest = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("99.99"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .customerEmail("test@example.com")
                .build();
    }

    private void mockForSuccessfulPayment() {
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
    void processPayment_foreignCurrencyConverted() {
        validRequest.setCurrency("EUR");
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

        PaymentResponse response = paymentService.processPayment(validRequest);
        assertNotNull(response);
        verify(currencyConverter).convertToUsd(any(BigDecimal.class), eq("EUR"));
    }

    @Test
    void processPayment_unsupportedCurrency() {
        validRequest.setCurrency("XYZ");
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(currencyConverter.convertToUsd(any(BigDecimal.class), eq("XYZ"))).thenReturn(null);

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));
        assertEquals("UNSUPPORTED_CURRENCY", ex.getErrorCode());
    }

    // --- Wire transfer validations ---

    @Test
    void processPayment_wireTransferBelowMinimum() {
        validRequest.setPaymentType(PaymentType.WIRE);
        validRequest.setAmount(new BigDecimal("50.00"));
        validRequest.setCustomerName("John Doe");
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));
        assertEquals("WIRE_MINIMUM_NOT_MET", ex.getErrorCode());
    }

    @Test
    void processPayment_wireTransferMissingName() {
        validRequest.setPaymentType(PaymentType.WIRE);
        validRequest.setAmount(new BigDecimal("500.00"));
        validRequest.setCustomerName(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));
        assertEquals("WIRE_NAME_REQUIRED", ex.getErrorCode());
    }

    @Test
    void processPayment_wireTransferEmptyName() {
        validRequest.setPaymentType(PaymentType.WIRE);
        validRequest.setAmount(new BigDecimal("500.00"));
        validRequest.setCustomerName("   ");
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));
        assertEquals("WIRE_NAME_REQUIRED", ex.getErrorCode());
    }

    @Test
    void processPayment_wireTransferSuccess() {
        validRequest.setPaymentType(PaymentType.WIRE);
        validRequest.setAmount(new BigDecimal("500.00"));
        validRequest.setCustomerName("John Doe");
        mockForSuccessfulPayment();

        PaymentResponse response = paymentService.processPayment(validRequest);
        assertNotNull(response);
    }

    // --- ACH limit checks ---

    @Test
    void processPayment_achDailyLimitExceeded() {
        validRequest.setPaymentType(PaymentType.ACH);
        validRequest.setAmount(new BigDecimal("1000.00"));
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(new BigDecimal("24500.00"));

        assertThrows(InsufficientFundsException.class,
                () -> paymentService.processPayment(validRequest));
    }

    @Test
    void processPayment_achWithinDailyLimit() {
        validRequest.setPaymentType(PaymentType.ACH);
        validRequest.setAmount(new BigDecimal("1000.00"));
        mockForSuccessfulPayment();

        PaymentResponse response = paymentService.processPayment(validRequest);
        assertNotNull(response);
    }

    // --- Credit card validations ---

    @Test
    void processPayment_creditCardMissingCardInfo() {
        validRequest.setPaymentType(PaymentType.CREDIT_CARD);
        validRequest.setCardLastFour(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));
        assertEquals("CARD_INFO_REQUIRED", ex.getErrorCode());
    }

    @Test
    void processPayment_creditCardInvalidCardLength() {
        validRequest.setPaymentType(PaymentType.CREDIT_CARD);
        validRequest.setCardLastFour("123");
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));
        assertEquals("CARD_INFO_REQUIRED", ex.getErrorCode());
    }

    // --- Debit card validations ---

    @Test
    void processPayment_debitCardMissingCardInfo() {
        validRequest.setPaymentType(PaymentType.DEBIT);
        validRequest.setCardLastFour(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));
        assertEquals("CARD_INFO_REQUIRED", ex.getErrorCode());
    }

    @Test
    void processPayment_debitCardSuccess() {
        validRequest.setPaymentType(PaymentType.DEBIT);
        validRequest.setCardLastFour("1234");
        mockForSuccessfulPayment();

        PaymentResponse response = paymentService.processPayment(validRequest);
        assertNotNull(response);
    }

    // --- Merchant daily limit ---

    @Test
    void processPayment_merchantDailyLimitExceeded() {
        testMerchant.setDailyLimit(new BigDecimal("1000.00"));
        validRequest.setAmount(new BigDecimal("500.00"));
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(paymentRepository.sumCompletedAmountByMerchantSince(anyLong(), any()))
                .thenReturn(new BigDecimal("600.00"));
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());

        assertThrows(InsufficientFundsException.class,
                () -> paymentService.processPayment(validRequest));
    }

    @Test
    void processPayment_merchantNoDailyLimit() {
        testMerchant.setDailyLimit(null);
        mockForSuccessfulPayment();

        PaymentResponse response = paymentService.processPayment(validRequest);
        assertNotNull(response);
    }

    @Test
    void processPayment_merchantDailyLimitNullTotal() {
        testMerchant.setDailyLimit(new BigDecimal("100000.00"));
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

        PaymentResponse response = paymentService.processPayment(validRequest);
        assertNotNull(response);
    }

    // --- Velocity check ---

    @Test
    void processPayment_velocityExceeded() {
        validRequest.setPaymentType(PaymentType.CREDIT_CARD);
        validRequest.setCardLastFour("4242");
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        // Return 10 recent payments to trigger velocity check
        List<Payment> recentPayments = Collections.nCopies(10, Payment.builder().build());
        when(paymentRepository.findByMerchantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(recentPayments);

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validRequest));
        assertEquals("VELOCITY_EXCEEDED", ex.getErrorCode());
    }

    // --- Notification failure ---

    @Test
    void processPayment_notificationFailureDoesNotBreakPayment() {
        mockForSuccessfulPayment();
        doThrow(new RuntimeException("Notification failed")).when(notificationService)
                .sendPaymentNotification(any(Payment.class));

        PaymentResponse response = paymentService.processPayment(validRequest);
        assertNotNull(response);
    }

    // --- getPaymentById ---

    @Test
    void getPaymentById_found() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-FOUND")
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentById(1L);
        assertEquals("TXN-FOUND", response.getTransactionId());
        assertEquals(new BigDecimal("100.00"), response.getAmount());
    }

    // --- getPaymentByTransactionId ---

    @Test
    void getPaymentByTransactionId_found() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-LOOKUP")
                .merchantId(1L)
                .amount(new BigDecimal("75.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.WIRE)
                .build();
        when(paymentRepository.findByTransactionId("TXN-LOOKUP")).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentByTransactionId("TXN-LOOKUP");
        assertEquals("TXN-LOOKUP", response.getTransactionId());
    }

    @Test
    void getPaymentByTransactionId_notFound() {
        when(paymentRepository.findByTransactionId("TXN-MISSING")).thenReturn(Optional.empty());

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.getPaymentByTransactionId("TXN-MISSING"));
        assertEquals("PAYMENT_NOT_FOUND", ex.getErrorCode());
    }

    // --- getPaymentsByMerchant ---

    @Test
    void getPaymentsByMerchant_returnsList() {
        List<Payment> payments = Arrays.asList(
                Payment.builder().id(1L).transactionId("TXN-1").merchantId(1L)
                        .amount(new BigDecimal("50.00")).currency("USD")
                        .status(PaymentStatus.COMPLETED).paymentType(PaymentType.CREDIT_CARD).build(),
                Payment.builder().id(2L).transactionId("TXN-2").merchantId(1L)
                        .amount(new BigDecimal("75.00")).currency("USD")
                        .status(PaymentStatus.PENDING).paymentType(PaymentType.ACH).build()
        );
        when(paymentRepository.findByMerchantIdAndStatus(1L, null)).thenReturn(payments);

        List<PaymentResponse> result = paymentService.getPaymentsByMerchant(1L);
        assertEquals(2, result.size());
    }

    @Test
    void getPaymentsByMerchant_emptyList() {
        when(paymentRepository.findByMerchantIdAndStatus(999L, null)).thenReturn(Collections.emptyList());

        List<PaymentResponse> result = paymentService.getPaymentsByMerchant(999L);
        assertTrue(result.isEmpty());
    }

    // --- updatePaymentStatus ---

    @Test
    void updatePaymentStatus_success() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-UPDATE")
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.PROCESSING)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED);
        assertEquals(PaymentStatus.COMPLETED, response.getStatus());
        assertNotNull(response.getCompletedAt());
    }

    @Test
    void updatePaymentStatus_terminalStateThrows() {
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
    void updatePaymentStatus_terminalToRefundedAllowed() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-REFUND")
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
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
    void updatePaymentStatus_notFound() {
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(PaymentException.class,
                () -> paymentService.updatePaymentStatus(999L, PaymentStatus.COMPLETED));
    }

    @Test
    void updatePaymentStatus_pendingToProcessing() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-PEND")
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.PENDING)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentService.updatePaymentStatus(1L, PaymentStatus.PROCESSING);
        assertEquals(PaymentStatus.PROCESSING, response.getStatus());
    }

    // --- cancelPayment ---

    @Test
    void cancelPayment_notFound() {
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(PaymentException.class,
                () -> paymentService.cancelPayment(999L));
    }

    @Test
    void cancelPayment_processingStatus() {
        Payment payment = Payment.builder()
                .id(1L)
                .status(PaymentStatus.PROCESSING)
                .build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.cancelPayment(1L));
        assertEquals("CANNOT_CANCEL", ex.getErrorCode());
    }

    // --- Idempotency with empty key ---

    @Test
    void processPayment_emptyIdempotencyKey_processesNormally() {
        validRequest.setIdempotencyKey("");
        mockForSuccessfulPayment();

        PaymentResponse response = paymentService.processPayment(validRequest);
        assertNotNull(response);
        verify(paymentRepository, never()).findByIdempotencyKey(anyString());
    }
}
