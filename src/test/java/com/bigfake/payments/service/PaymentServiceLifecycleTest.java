package com.bigfake.payments.service;

import com.bigfake.payments.exception.PaymentException;
import com.bigfake.payments.model.dto.PaymentResponse;
import com.bigfake.payments.model.entity.Payment;
import com.bigfake.payments.model.enums.PaymentStatus;
import com.bigfake.payments.model.enums.PaymentType;
import com.bigfake.payments.repository.MerchantRepository;
import com.bigfake.payments.repository.PaymentRepository;
import com.bigfake.payments.service.impl.PaymentServiceImpl;
import com.bigfake.payments.testsupport.TestFixtures;
import com.bigfake.payments.util.CurrencyConverter;
import com.bigfake.payments.util.PaymentValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the read and lifecycle operations of {@link PaymentServiceImpl}:
 * lookups, listing, status transitions and cancellation.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceLifecycleTest {

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

    @Test
    void getPaymentByIdMapsEveryResponseField() {
        LocalDateTime createdAt = LocalDateTime.of(2024, 1, 2, 3, 4, 5);
        LocalDateTime completedAt = createdAt.plusMinutes(1);
        Payment payment = TestFixtures.payment()
                .description("Order 99")
                .feeAmount(new BigDecimal("2.8997"))
                .netAmount(new BigDecimal("97.0903"))
                .gatewayReference("GW-1a2b3c4d")
                .failureReason(null)
                .createdAt(createdAt)
                .completedAt(completedAt)
                .build();
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentById(10L);

        assertEquals(10L, response.getId());
        assertEquals("TXN-0123456789ABCDEF", response.getTransactionId());
        assertEquals(TestFixtures.MERCHANT_ID, response.getMerchantId());
        assertEquals(new BigDecimal("99.99"), response.getAmount());
        assertEquals("USD", response.getCurrency());
        assertEquals(PaymentStatus.COMPLETED, response.getStatus());
        assertEquals(PaymentType.CREDIT_CARD, response.getPaymentType());
        assertEquals("Order 99", response.getDescription());
        assertEquals("customer@example.com", response.getCustomerEmail());
        assertEquals(new BigDecimal("2.8997"), response.getFeeAmount());
        assertEquals(new BigDecimal("97.0903"), response.getNetAmount());
        assertEquals("GW-1a2b3c4d", response.getGatewayReference());
        assertNull(response.getFailureReason());
        assertEquals(createdAt, response.getCreatedAt());
        assertEquals(completedAt, response.getCompletedAt());
    }

    @Test
    void getPaymentByIdThrowsWhenMissing() {
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.getPaymentById(999L));

        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
        assertEquals("Payment not found: 999", exception.getMessage());
    }

    @Test
    void getPaymentByTransactionIdReturnsTheMatchingPayment() {
        when(paymentRepository.findByTransactionId("TXN-0123456789ABCDEF"))
                .thenReturn(Optional.of(TestFixtures.payment().build()));

        PaymentResponse response = paymentService.getPaymentByTransactionId("TXN-0123456789ABCDEF");

        assertEquals("TXN-0123456789ABCDEF", response.getTransactionId());
    }

    @Test
    void getPaymentByTransactionIdThrowsWhenMissing() {
        when(paymentRepository.findByTransactionId("TXN-NOPE")).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.getPaymentByTransactionId("TXN-NOPE"));

        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void getPaymentsByMerchantMapsEveryPayment() {
        when(paymentRepository.findByMerchantIdAndStatus(TestFixtures.MERCHANT_ID, null))
                .thenReturn(Arrays.asList(
                        TestFixtures.payment().id(1L).transactionId("TXN-1").build(),
                        TestFixtures.payment().id(2L).transactionId("TXN-2").build()));

        List<PaymentResponse> responses = paymentService.getPaymentsByMerchant(TestFixtures.MERCHANT_ID);

        assertEquals(2, responses.size());
        assertEquals("TXN-1", responses.get(0).getTransactionId());
        assertEquals("TXN-2", responses.get(1).getTransactionId());
    }

    @Test
    void getPaymentsByMerchantReturnsEmptyListWhenMerchantHasNoPayments() {
        when(paymentRepository.findByMerchantIdAndStatus(7L, null)).thenReturn(Collections.emptyList());

        assertTrue(paymentService.getPaymentsByMerchant(7L).isEmpty());
    }

    @Test
    void updatePaymentStatusStampsCompletedAtWhenCompleting() {
        Payment payment = TestFixtures.payment().status(PaymentStatus.PROCESSING).completedAt(null).build();
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        PaymentResponse response = paymentService.updatePaymentStatus(10L, PaymentStatus.COMPLETED);

        assertEquals(PaymentStatus.COMPLETED, response.getStatus());
        assertNotNull(response.getCompletedAt());
    }

    @Test
    void updatePaymentStatusLeavesCompletedAtUnsetForNonCompletingTransitions() {
        Payment payment = TestFixtures.payment().status(PaymentStatus.PENDING).completedAt(null).build();
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        PaymentResponse response = paymentService.updatePaymentStatus(10L, PaymentStatus.PROCESSING);

        assertEquals(PaymentStatus.PROCESSING, response.getStatus());
        assertNull(response.getCompletedAt());
    }

    @Test
    void updatePaymentStatusAllowsRefundingATerminalPayment() {
        Payment payment = TestFixtures.payment().status(PaymentStatus.COMPLETED).build();
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        PaymentResponse response = paymentService.updatePaymentStatus(10L, PaymentStatus.REFUNDED);

        assertEquals(PaymentStatus.REFUNDED, response.getStatus());
    }

    @ParameterizedTest(name = "cannot move a {0} payment back to PROCESSING")
    @EnumSource(value = PaymentStatus.class, names = {"COMPLETED", "FAILED", "REFUNDED"})
    void updatePaymentStatusRejectsTransitionsOutOfTerminalStates(PaymentStatus terminalStatus) {
        Payment payment = TestFixtures.payment().status(terminalStatus).build();
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.updatePaymentStatus(10L, PaymentStatus.PROCESSING));

        assertEquals("INVALID_STATE_TRANSITION", exception.getErrorCode());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void updatePaymentStatusThrowsWhenPaymentIsMissing() {
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.updatePaymentStatus(999L, PaymentStatus.COMPLETED));

        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void cancelPaymentFailsTheEntityWithACancellationReason() {
        Payment payment = TestFixtures.payment().status(PaymentStatus.PENDING).build();
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        paymentService.cancelPayment(10L);

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        assertEquals(PaymentStatus.FAILED, saved.getValue().getStatus());
        assertEquals("Cancelled by user", saved.getValue().getFailureReason());
    }

    @ParameterizedTest(name = "cannot cancel a {0} payment")
    @EnumSource(value = PaymentStatus.class, names = {"PROCESSING", "COMPLETED", "FAILED", "REFUNDED"})
    void cancelPaymentRejectsNonPendingPayments(PaymentStatus status) {
        Payment payment = TestFixtures.payment().status(status).build();
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.cancelPayment(10L));

        assertEquals("CANNOT_CANCEL", exception.getErrorCode());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void cancelPaymentThrowsWhenPaymentIsMissing() {
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.cancelPayment(999L));

        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
    }
}
