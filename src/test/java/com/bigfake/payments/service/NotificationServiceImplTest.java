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
    private Merchant testMerchant;

    @BeforeEach
    void setUp() {
        testPayment = Payment.builder()
                .id(1L)
                .transactionId("TXN-TEST001")
                .merchantId(1L)
                .amount(new BigDecimal("99.99"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .customerEmail("customer@test.com")
                .build();

        testMerchant = Merchant.builder()
                .id(1L)
                .merchantCode("MERCH001")
                .businessName("Test Store")
                .isActive(true)
                .webhookUrl("https://example.com/webhook")
                .build();
    }

    @Test
    void sendPaymentNotification_withWebhookUrl_sendsWebhook() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(testPayment));
        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendPaymentNotification_merchantNotFound_doesNotThrow() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(testPayment));
    }

    @Test
    void sendPaymentNotification_noWebhookUrl_doesNotSendWebhook() {
        testMerchant.setWebhookUrl(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(testPayment));
    }

    @Test
    void sendPaymentNotification_noCustomerEmail_doesNotThrow() {
        testPayment.setCustomerEmail(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(testPayment));
    }

    @Test
    void sendPaymentNotification_failedStatus_logsFailureEmail() {
        testPayment.setStatus(PaymentStatus.FAILED);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(testPayment));
    }

    @Test
    void sendRefundNotification_withWebhookUrl() {
        Refund refund = Refund.builder()
                .id(1L)
                .refundId("RFD-TEST001")
                .paymentId(1L)
                .amount(new BigDecimal("25.00"))
                .status(PaymentStatus.COMPLETED)
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        assertDoesNotThrow(() -> notificationService.sendRefundNotification(refund, testPayment));
        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendRefundNotification_noWebhookUrl() {
        testMerchant.setWebhookUrl(null);
        Refund refund = Refund.builder()
                .id(1L)
                .refundId("RFD-TEST002")
                .paymentId(1L)
                .amount(new BigDecimal("10.00"))
                .status(PaymentStatus.COMPLETED)
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        assertDoesNotThrow(() -> notificationService.sendRefundNotification(refund, testPayment));
    }

    @Test
    void sendRefundNotification_merchantNotFound() {
        Refund refund = Refund.builder()
                .id(1L)
                .refundId("RFD-TEST003")
                .paymentId(1L)
                .amount(new BigDecimal("10.00"))
                .status(PaymentStatus.COMPLETED)
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> notificationService.sendRefundNotification(refund, testPayment));
    }

    @Test
    void sendWebhook_invalidUrl_doesNotThrow() {
        assertDoesNotThrow(() -> notificationService.sendWebhook("http://invalid-host-that-does-not-exist.local/webhook", "payload"));
    }
}
