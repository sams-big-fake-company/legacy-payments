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
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private MerchantRepository merchantRepository;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private Payment testPayment;
    private Merchant merchantWithWebhook;
    private Merchant merchantWithoutWebhook;

    @BeforeEach
    void setUp() {
        testPayment = Payment.builder()
                .id(1L)
                .transactionId("TXN-NOTIFY001")
                .merchantId(1L)
                .amount(new BigDecimal("99.99"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .customerEmail("customer@example.com")
                .build();

        merchantWithWebhook = Merchant.builder()
                .id(1L)
                .merchantCode("MERCH001")
                .businessName("Test Store")
                .webhookUrl("https://example.com/webhook")
                .build();

        merchantWithoutWebhook = Merchant.builder()
                .id(2L)
                .merchantCode("MERCH002")
                .businessName("No Webhook Store")
                .webhookUrl(null)
                .build();
    }

    @Test
    void sendPaymentNotification_withWebhookUrl() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithWebhook));

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(testPayment));
        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendPaymentNotification_withoutWebhookUrl() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithoutWebhook));

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(testPayment));
    }

    @Test
    void sendPaymentNotification_merchantNotFound() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(testPayment));
    }

    @Test
    void sendPaymentNotification_failedPaymentWithEmail() {
        testPayment.setStatus(PaymentStatus.FAILED);
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(testPayment));
    }

    @Test
    void sendPaymentNotification_noCustomerEmail() {
        testPayment.setCustomerEmail(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(testPayment));
    }

    @Test
    void sendPaymentNotification_emptyCustomerEmail() {
        testPayment.setCustomerEmail("");
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(testPayment));
    }

    @Test
    void sendPaymentNotification_pendingStatusNoEmailSent() {
        testPayment.setStatus(PaymentStatus.PENDING);
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(testPayment));
    }

    @Test
    void sendRefundNotification_withWebhookUrl() {
        Refund refund = Refund.builder()
                .id(1L)
                .refundId("RFD-NOTIFY001")
                .paymentId(1L)
                .amount(new BigDecimal("50.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithWebhook));

        assertDoesNotThrow(() -> notificationService.sendRefundNotification(refund, testPayment));
    }

    @Test
    void sendRefundNotification_withoutWebhookUrl() {
        Refund refund = Refund.builder()
                .id(1L)
                .refundId("RFD-NOTIFY002")
                .paymentId(1L)
                .amount(new BigDecimal("50.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithoutWebhook));

        assertDoesNotThrow(() -> notificationService.sendRefundNotification(refund, testPayment));
    }

    @Test
    void sendRefundNotification_merchantNotFound() {
        Refund refund = Refund.builder()
                .id(1L)
                .refundId("RFD-NOTIFY003")
                .paymentId(1L)
                .amount(new BigDecimal("50.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> notificationService.sendRefundNotification(refund, testPayment));
    }

    @Test
    void sendWebhook_handlesExceptionGracefully() {
        assertDoesNotThrow(() ->
                notificationService.sendWebhook("http://invalid-url-that-will-fail", "payload"));
    }
}
