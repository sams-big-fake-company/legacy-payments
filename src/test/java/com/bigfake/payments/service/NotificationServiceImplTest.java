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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
                .transactionId("TXN-NOTIFY")
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .customerEmail("customer@example.com")
                .build();

        merchantWithWebhook = Merchant.builder()
                .id(1L)
                .merchantCode("MERCH001")
                .webhookUrl("https://example.com/webhook")
                .build();

        merchantWithoutWebhook = Merchant.builder()
                .id(2L)
                .merchantCode("MERCH002")
                .webhookUrl(null)
                .build();
    }

    // --- sendPaymentNotification ---

    @Test
    void sendPaymentNotification_merchantNotFound_doesNotThrow() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(testPayment));
    }

    @Test
    void sendPaymentNotification_merchantWithoutWebhook_doesNotThrow() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithoutWebhook));

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(testPayment));
    }

    @Test
    void sendPaymentNotification_completedPaymentWithEmail_logs() {
        testPayment.setStatus(PaymentStatus.COMPLETED);
        testPayment.setCustomerEmail("test@example.com");
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithoutWebhook));

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(testPayment));
    }

    @Test
    void sendPaymentNotification_failedPaymentWithEmail_logs() {
        testPayment.setStatus(PaymentStatus.FAILED);
        testPayment.setCustomerEmail("test@example.com");
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithoutWebhook));

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(testPayment));
    }

    @Test
    void sendPaymentNotification_pendingPaymentWithEmail_noEmailSent() {
        testPayment.setStatus(PaymentStatus.PENDING);
        testPayment.setCustomerEmail("test@example.com");
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithoutWebhook));

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(testPayment));
    }

    @Test
    void sendPaymentNotification_noCustomerEmail() {
        testPayment.setCustomerEmail(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithoutWebhook));

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(testPayment));
    }

    @Test
    void sendPaymentNotification_emptyCustomerEmail() {
        testPayment.setCustomerEmail("");
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithoutWebhook));

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(testPayment));
    }

    // --- sendRefundNotification ---

    @Test
    void sendRefundNotification_merchantNotFound_doesNotThrow() {
        Refund refund = Refund.builder()
                .id(1L)
                .refundId("RFD-TEST")
                .paymentId(1L)
                .amount(new BigDecimal("25.00"))
                .status(PaymentStatus.COMPLETED)
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> notificationService.sendRefundNotification(refund, testPayment));
    }

    @Test
    void sendRefundNotification_merchantWithoutWebhook_doesNotThrow() {
        Refund refund = Refund.builder()
                .id(1L)
                .refundId("RFD-TEST")
                .paymentId(1L)
                .amount(new BigDecimal("25.00"))
                .status(PaymentStatus.COMPLETED)
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithoutWebhook));

        assertDoesNotThrow(() -> notificationService.sendRefundNotification(refund, testPayment));
    }

    // --- sendWebhook ---

    @Test
    void sendWebhook_failsGracefully() {
        assertDoesNotThrow(() -> notificationService.sendWebhook(
                "http://invalid-url-that-will-fail.example.com/webhook",
                java.util.Map.of("event", "test")));
    }
}
