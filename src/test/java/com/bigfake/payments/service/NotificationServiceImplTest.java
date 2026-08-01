package com.bigfake.payments.service;

import com.bigfake.payments.model.entity.Merchant;
import com.bigfake.payments.model.entity.Payment;
import com.bigfake.payments.model.entity.Refund;
import com.bigfake.payments.model.enums.PaymentStatus;
import com.bigfake.payments.repository.MerchantRepository;
import com.bigfake.payments.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NotificationServiceImpl.
 *
 * Webhook delivery uses a spy to avoid real HTTP calls; the unreachable-URL
 * test exercises the internal error handling of sendWebhook.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private MerchantRepository merchantRepository;

    @Spy
    @InjectMocks
    private NotificationServiceImpl notificationService;

    private Payment payment;
    private Merchant merchantWithWebhook;

    @BeforeEach
    void setUp() {
        payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-NOTIFY1")
                .merchantId(1L)
                .amount(new BigDecimal("25.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .customerEmail("customer@example.com")
                .build();

        merchantWithWebhook = Merchant.builder()
                .id(1L)
                .merchantCode("MERCH001")
                .businessName("Test Store")
                .webhookUrl("https://merchant.example.com/webhook")
                .build();
    }

    @Test
    void sendPaymentNotification_merchantWithWebhook_sendsWebhook() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithWebhook));
        doNothing().when(notificationService).sendWebhook(any(), any());

        notificationService.sendPaymentNotification(payment);

        verify(notificationService).sendWebhook(eq("https://merchant.example.com/webhook"), any());
    }

    @Test
    void sendPaymentNotification_merchantWithoutWebhook_skipsWebhook() {
        merchantWithWebhook.setWebhookUrl(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithWebhook));

        notificationService.sendPaymentNotification(payment);

        verify(notificationService, never()).sendWebhook(any(), any());
    }

    @Test
    void sendPaymentNotification_merchantNotFound_skipsWebhook() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());

        notificationService.sendPaymentNotification(payment);

        verify(notificationService, never()).sendWebhook(any(), any());
    }

    @Test
    void sendPaymentNotification_failedPaymentWithEmail_doesNotThrow() {
        payment.setStatus(PaymentStatus.FAILED);
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(payment));
    }

    @Test
    void sendPaymentNotification_pendingPaymentWithEmail_doesNotThrow() {
        payment.setStatus(PaymentStatus.PENDING);
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(payment));
    }

    @Test
    void sendPaymentNotification_noCustomerEmail_doesNotThrow() {
        payment.setCustomerEmail(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(payment));
    }

    @Test
    void sendRefundNotification_merchantWithWebhook_sendsWebhook() {
        Refund refund = Refund.builder()
                .refundId("RFD-NOTIFY1")
                .paymentId(1L)
                .amount(new BigDecimal("10.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithWebhook));
        doNothing().when(notificationService).sendWebhook(any(), any());

        notificationService.sendRefundNotification(refund, payment);

        verify(notificationService).sendWebhook(eq("https://merchant.example.com/webhook"), any());
    }

    @Test
    void sendRefundNotification_merchantWithoutWebhook_skipsWebhook() {
        Refund refund = Refund.builder()
                .refundId("RFD-NOTIFY2")
                .paymentId(1L)
                .amount(new BigDecimal("10.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        merchantWithWebhook.setWebhookUrl(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithWebhook));

        notificationService.sendRefundNotification(refund, payment);

        verify(notificationService, never()).sendWebhook(any(), any());
    }

    @Test
    void sendWebhook_deliveryFailure_isSwallowed() {
        // Unreachable local port fails fast with connection refused
        assertDoesNotThrow(() ->
                notificationService.sendWebhook("http://127.0.0.1:1/webhook", "payload"));
    }
}
