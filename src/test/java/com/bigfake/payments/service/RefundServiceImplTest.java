package com.bigfake.payments.service;

import com.bigfake.payments.exception.PaymentException;
import com.bigfake.payments.model.dto.RefundRequest;
import com.bigfake.payments.model.entity.Payment;
import com.bigfake.payments.model.entity.Refund;
import com.bigfake.payments.model.enums.PaymentStatus;
import com.bigfake.payments.repository.PaymentRepository;
import com.bigfake.payments.repository.RefundRepository;
import com.bigfake.payments.service.impl.RefundServiceImpl;
import com.bigfake.payments.testsupport.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RefundServiceImpl}: refundable-state rules, partial refund
 * accumulation and the payment status side effect.
 */
@ExtendWith(MockitoExtension.class)
class RefundServiceImplTest {

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private RefundServiceImpl refundService;

    private void givenRefundsAreSaved() {
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> {
            Refund saved = invocation.getArgument(0);
            saved.setId(20L);
            return saved;
        });
    }

    @Test
    void processRefundCompletesTheRefundAndMarksThePaymentRefunded() {
        Payment payment = TestFixtures.payment().build();
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));
        when(refundRepository.findByPaymentId(10L)).thenReturn(Collections.emptyList());
        givenRefundsAreSaved();

        Refund refund = refundService.processRefund(TestFixtures.refundRequest()
                .amount(new BigDecimal("25.00"))
                .initiatedBy("support-agent")
                .build());

        assertEquals(PaymentStatus.COMPLETED, refund.getStatus());
        assertEquals(new BigDecimal("25.00"), refund.getAmount());
        assertEquals(10L, refund.getPaymentId());
        assertEquals("support-agent", refund.getInitiatedBy());
        assertTrue(refund.getRefundId().startsWith("RFD-"));
        assertNotNull(refund.getProcessedAt());
        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
        verify(paymentRepository).save(payment);
        verify(notificationService).sendRefundNotification(refund, payment);
    }

    @Test
    void processRefundPersistsTheRefundBeforeAndAfterTheGatewayCall() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(TestFixtures.payment().build()));
        when(refundRepository.findByPaymentId(10L)).thenReturn(Collections.emptyList());
        givenRefundsAreSaved();

        refundService.processRefund(TestFixtures.refundRequest().build());

        ArgumentCaptor<Refund> saves = ArgumentCaptor.forClass(Refund.class);
        verify(refundRepository, times(2)).save(saves.capture());
        assertEquals(PaymentStatus.COMPLETED, saves.getValue().getStatus());
    }

    @Test
    void processRefundDefaultsTheInitiatorToSystem() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(TestFixtures.payment().build()));
        when(refundRepository.findByPaymentId(10L)).thenReturn(Collections.emptyList());
        givenRefundsAreSaved();

        Refund refund = refundService.processRefund(
                TestFixtures.refundRequest().initiatedBy(null).build());

        assertEquals("system", refund.getInitiatedBy());
    }

    @Test
    void processRefundThrowsWhenPaymentIsMissing() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class,
                () -> refundService.processRefund(TestFixtures.refundRequest().build()));

        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
        verify(refundRepository, never()).save(any(Refund.class));
    }

    @ParameterizedTest(name = "a {0} payment cannot be refunded")
    @EnumSource(value = PaymentStatus.class, names = {"PENDING", "PROCESSING", "FAILED", "REFUNDED"})
    void processRefundRejectsPaymentsThatAreNotCompleted(PaymentStatus status) {
        when(paymentRepository.findById(10L))
                .thenReturn(Optional.of(TestFixtures.payment().status(status).build()));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> refundService.processRefund(TestFixtures.refundRequest().build()));

        assertEquals("INVALID_REFUND_STATE", exception.getErrorCode());
    }

    @Test
    void processRefundRejectsAmountLargerThanTheOriginalPayment() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(TestFixtures.payment().build()));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> refundService.processRefund(
                        TestFixtures.refundRequest().amount(new BigDecimal("100.00")).build()));

        assertEquals("REFUND_EXCEEDS_PAYMENT", exception.getErrorCode());
        verify(refundRepository, never()).save(any(Refund.class));
    }

    @Test
    void processRefundAllowsRefundingTheFullPaymentAmount() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(TestFixtures.payment().build()));
        when(refundRepository.findByPaymentId(10L)).thenReturn(Collections.emptyList());
        givenRefundsAreSaved();

        Refund refund = refundService.processRefund(
                TestFixtures.refundRequest().amount(new BigDecimal("99.99")).build());

        assertEquals(PaymentStatus.COMPLETED, refund.getStatus());
    }

    @Test
    void processRefundRejectsWhenEarlierRefundsAlreadyUsedUpThePayment() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(TestFixtures.payment().build()));
        when(refundRepository.findByPaymentId(10L)).thenReturn(Arrays.asList(
                TestFixtures.refund().amount(new BigDecimal("60.00")).build(),
                TestFixtures.refund().amount(new BigDecimal("35.00")).build()));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> refundService.processRefund(
                        TestFixtures.refundRequest().amount(new BigDecimal("10.00")).build()));

        assertEquals("REFUND_TOTAL_EXCEEDED", exception.getErrorCode());
    }

    @Test
    void processRefundIgnoresNonCompletedRefundsWhenTotallingPreviousRefunds() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(TestFixtures.payment().build()));
        when(refundRepository.findByPaymentId(10L)).thenReturn(Arrays.asList(
                TestFixtures.refund().amount(new BigDecimal("90.00")).status(PaymentStatus.FAILED).build(),
                TestFixtures.refund().amount(new BigDecimal("50.00")).status(PaymentStatus.PROCESSING).build()));
        givenRefundsAreSaved();

        Refund refund = refundService.processRefund(
                TestFixtures.refundRequest().amount(new BigDecimal("99.99")).build());

        assertEquals(PaymentStatus.COMPLETED, refund.getStatus());
    }

    @Test
    void processRefundStillReturnsWhenTheNotificationFails() {
        Payment payment = TestFixtures.payment().build();
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));
        when(refundRepository.findByPaymentId(10L)).thenReturn(Collections.emptyList());
        givenRefundsAreSaved();
        doThrow(new IllegalStateException("webhook endpoint down"))
                .when(notificationService).sendRefundNotification(any(Refund.class), any(Payment.class));

        Refund refund = refundService.processRefund(TestFixtures.refundRequest().build());

        assertEquals(PaymentStatus.COMPLETED, refund.getStatus());
    }

    @Test
    void getRefundByIdReturnsTheRefund() {
        when(refundRepository.findById(20L)).thenReturn(Optional.of(TestFixtures.refund().build()));

        assertEquals("RFD-ABCDEF123456", refundService.getRefundById(20L).getRefundId());
    }

    @Test
    void getRefundByIdThrowsWhenMissing() {
        when(refundRepository.findById(999L)).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class,
                () -> refundService.getRefundById(999L));

        assertEquals("REFUND_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void getRefundsForPaymentDelegatesToTheRepository() {
        List<Refund> stored = Collections.singletonList(TestFixtures.refund().build());
        when(refundRepository.findByPaymentId(10L)).thenReturn(stored);

        assertEquals(stored, refundService.getRefundsForPayment(10L));
    }

    @Test
    void refundRequestCarriesTheReasonThrough() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(TestFixtures.payment().build()));
        when(refundRepository.findByPaymentId(10L)).thenReturn(Collections.emptyList());
        givenRefundsAreSaved();
        RefundRequest request = TestFixtures.refundRequest().reason("Duplicate charge").build();

        assertEquals("Duplicate charge", refundService.processRefund(request).getReason());
    }
}
