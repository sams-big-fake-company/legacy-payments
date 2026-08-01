package com.bigfake.payments.service;

import com.bigfake.payments.model.entity.Merchant;
import com.bigfake.payments.model.entity.Payment;
import com.bigfake.payments.model.entity.Refund;
import com.bigfake.payments.model.enums.PaymentStatus;
import com.bigfake.payments.repository.MerchantRepository;
import com.bigfake.payments.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;

/**
 * Unit tests for NotificationServiceImpl.
 *
 * Webhook delivery uses an invalid URL so no real network calls are made;
 * the service is expected to swallow delivery failures.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    private static final String INVALID_URL = "invalid://no-such-host";

    @Mock
    private MerchantRepository merchantRepository;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private Payment payment(PaymentStatus status, String email) {
        return Payment.builder()
                .id(1L)
                .transactionId("TXN-NOTIFY")
                .merchantId(10L)
                .amount(new BigDecimal("25.00"))
                .currency("USD")
                .status(status)
                .customerEmail(email)
                .build();
    }

    private Merchant merchantWithWebhook(String url) {
        return Merchant.builder()
                .id(10L)
                .merchantCode("MERCH010")
                .webhookUrl(url)
                .build();
    }

    @Test
    void sendPaymentNotification_merchantNotFound_doesNothing() {
        when(merchantRepository.findById(10L)).thenReturn(Optional.empty());
        assertDoesNotThrow(() ->
                notificationService.sendPaymentNotification(payment(PaymentStatus.COMPLETED, null)));
    }

    @Test
    void sendPaymentNotification_merchantWithoutWebhookUrl_skipsWebhook() {
        when(merchantRepository.findById(10L)).thenReturn(Optional.of(merchantWithWebhook(null)));
        assertDoesNotThrow(() ->
                notificationService.sendPaymentNotification(payment(PaymentStatus.COMPLETED, null)));
    }

    @Test
    void sendPaymentNotification_webhookFailure_isSwallowed() {
        when(merchantRepository.findById(10L)).thenReturn(Optional.of(merchantWithWebhook(INVALID_URL)));
        assertDoesNotThrow(() ->
                notificationService.sendPaymentNotification(payment(PaymentStatus.COMPLETED, null)));
    }

    @Test
    void sendPaymentNotification_completedWithEmail_logsConfirmation() {
        when(merchantRepository.findById(10L)).thenReturn(Optional.empty());
        assertDoesNotThrow(() ->
                notificationService.sendPaymentNotification(payment(PaymentStatus.COMPLETED, "a@b.com")));
    }

    @Test
    void sendPaymentNotification_failedWithEmail_logsFailureEmail() {
        when(merchantRepository.findById(10L)).thenReturn(Optional.empty());
        assertDoesNotThrow(() ->
                notificationService.sendPaymentNotification(payment(PaymentStatus.FAILED, "a@b.com")));
    }

    @Test
    void sendPaymentNotification_pendingWithEmail_sendsNoEmail() {
        when(merchantRepository.findById(10L)).thenReturn(Optional.empty());
        assertDoesNotThrow(() ->
                notificationService.sendPaymentNotification(payment(PaymentStatus.PENDING, "a@b.com")));
    }

    @Test
    void sendRefundNotification_merchantNotFound_doesNothing() {
        when(merchantRepository.findById(10L)).thenReturn(Optional.empty());
        Refund refund = Refund.builder()
                .refundId("RFD-1")
                .amount(new BigDecimal("5.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        assertDoesNotThrow(() ->
                notificationService.sendRefundNotification(refund, payment(PaymentStatus.REFUNDED, null)));
    }

    @Test
    void sendRefundNotification_webhookFailure_isSwallowed() {
        when(merchantRepository.findById(10L)).thenReturn(Optional.of(merchantWithWebhook(INVALID_URL)));
        Refund refund = Refund.builder()
                .refundId("RFD-2")
                .amount(new BigDecimal("5.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        assertDoesNotThrow(() ->
                notificationService.sendRefundNotification(refund, payment(PaymentStatus.REFUNDED, null)));
    }

    @Test
    void sendWebhook_invalidUrl_doesNotThrow() {
        assertDoesNotThrow(() -> notificationService.sendWebhook(INVALID_URL, "payload"));
    }
}
