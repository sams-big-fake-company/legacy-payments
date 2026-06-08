package com.bigfake.payments.service;

import com.bigfake.payments.model.entity.Merchant;
import com.bigfake.payments.model.entity.Payment;
import com.bigfake.payments.model.entity.Refund;
import com.bigfake.payments.model.enums.PaymentStatus;
import com.bigfake.payments.model.enums.PaymentType;
import com.bigfake.payments.repository.MerchantRepository;
import com.bigfake.payments.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
                .transactionId("TXN-NOTIFY123")
                .merchantId(1L)
                .amount(new BigDecimal("99.99"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .customerEmail("customer@example.com")
                .build();

        merchantWithWebhook = Merchant.builder()
                .id(1L)
                .merchantCode("MERCH001")
                .businessName("Test Store")
                .webhookUrl("https://merchant.example.com/webhook")
                .isActive(true)
                .build();

        merchantWithoutWebhook = Merchant.builder()
                .id(2L)
                .merchantCode("MERCH002")
                .businessName("No Webhook Store")
                .webhookUrl(null)
                .isActive(true)
                .build();
    }

    @Test
    void sendPaymentNotification_merchantNotFound_doesNotThrow() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(testPayment));
    }

    @Test
    void sendPaymentNotification_merchantWithoutWebhook_noWebhookSent() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithoutWebhook));
        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(testPayment));
    }

    @Test
    void sendPaymentNotification_completedPayment_logsEmail() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithoutWebhook));
        testPayment.setStatus(PaymentStatus.COMPLETED);
        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(testPayment));
    }

    @Test
    void sendPaymentNotification_failedPayment_logsEmail() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithoutWebhook));
        testPayment.setStatus(PaymentStatus.FAILED);
        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(testPayment));
    }

    @Test
    void sendPaymentNotification_noCustomerEmail_noEmailSent() {
        testPayment.setCustomerEmail(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithoutWebhook));
        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(testPayment));
    }

    @Test
    void sendPaymentNotification_emptyCustomerEmail_noEmailSent() {
        testPayment.setCustomerEmail("");
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithoutWebhook));
        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(testPayment));
    }

    @Test
    void sendPaymentNotification_pendingStatus_noEmailSent() {
        testPayment.setStatus(PaymentStatus.PENDING);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithoutWebhook));
        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(testPayment));
    }

    @Test
    void sendRefundNotification_merchantNotFound_doesNotThrow() {
        Refund refund = Refund.builder()
                .id(1L)
                .refundId("RFD-TEST")
                .paymentId(1L)
                .amount(new BigDecimal("50.00"))
                .status(PaymentStatus.COMPLETED)
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> notificationService.sendRefundNotification(refund, testPayment));
    }

    @Test
    void sendRefundNotification_merchantWithoutWebhook_noWebhookSent() {
        Refund refund = Refund.builder()
                .id(1L)
                .refundId("RFD-TEST")
                .paymentId(1L)
                .amount(new BigDecimal("50.00"))
                .status(PaymentStatus.COMPLETED)
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithoutWebhook));
        assertDoesNotThrow(() -> notificationService.sendRefundNotification(refund, testPayment));
    }

    @Test
    void sendWebhook_exceptionSwallowed() {
        assertDoesNotThrow(() -> notificationService.sendWebhook("http://invalid-url-that-will-fail", "payload"));
    }
}
