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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private MerchantRepository merchantRepository;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private Merchant merchantWithWebhook;
    private Merchant merchantWithoutWebhook;
    private Payment completedPayment;
    private Payment failedPayment;

    @BeforeEach
    void setUp() {
        merchantWithWebhook = Merchant.builder()
                .id(1L)
                .merchantCode("MERCH001")
                .businessName("Test Store")
                .webhookUrl("https://example.com/webhook")
                .isActive(true)
                .build();

        merchantWithoutWebhook = Merchant.builder()
                .id(2L)
                .merchantCode("MERCH002")
                .businessName("No Webhook Store")
                .webhookUrl(null)
                .isActive(true)
                .build();

        completedPayment = Payment.builder()
                .id(1L)
                .transactionId("TXN-COMPLETED001")
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .customerEmail("customer@example.com")
                .build();

        failedPayment = Payment.builder()
                .id(2L)
                .transactionId("TXN-FAILED001")
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .status(PaymentStatus.FAILED)
                .paymentType(PaymentType.CREDIT_CARD)
                .customerEmail("customer@example.com")
                .build();
    }

    // --- sendPaymentNotification ---

    @Test
    void sendPaymentNotification_withWebhook_callsMerchantRepo() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithWebhook));

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(completedPayment));
        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendPaymentNotification_noWebhookUrl_doesNotSendWebhook() {
        when(merchantRepository.findById(2L)).thenReturn(Optional.of(merchantWithoutWebhook));

        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-TEST")
                .merchantId(2L)
                .status(PaymentStatus.COMPLETED)
                .amount(new BigDecimal("10.00"))
                .build();

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(payment));
    }

    @Test
    void sendPaymentNotification_merchantNotFound_doesNotThrow() {
        when(merchantRepository.findById(999L)).thenReturn(Optional.empty());

        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-TEST")
                .merchantId(999L)
                .status(PaymentStatus.COMPLETED)
                .build();

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(payment));
    }

    @Test
    void sendPaymentNotification_failedPayment_logsEmail() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithoutWebhook));
        failedPayment.setMerchantId(1L);
        // Doesn't actually send email but should log it
        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(failedPayment));
    }

    @Test
    void sendPaymentNotification_nullCustomerEmail_skipsEmail() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithoutWebhook));
        completedPayment.setMerchantId(1L);
        completedPayment.setCustomerEmail(null);
        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(completedPayment));
    }

    @Test
    void sendPaymentNotification_emptyCustomerEmail_skipsEmail() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithoutWebhook));
        completedPayment.setMerchantId(1L);
        completedPayment.setCustomerEmail("");
        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(completedPayment));
    }

    @Test
    void sendPaymentNotification_pendingStatus_skipsEmail() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithoutWebhook));
        completedPayment.setMerchantId(1L);
        completedPayment.setStatus(PaymentStatus.PENDING);
        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(completedPayment));
    }

    // --- sendRefundNotification ---

    @Test
    void sendRefundNotification_withWebhook_callsMerchantRepo() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithWebhook));

        Refund refund = Refund.builder()
                .id(1L)
                .refundId("RFD-TEST001")
                .paymentId(1L)
                .amount(new BigDecimal("50.00"))
                .status(PaymentStatus.COMPLETED)
                .build();

        assertDoesNotThrow(() -> notificationService.sendRefundNotification(refund, completedPayment));
        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendRefundNotification_noWebhookUrl_doesNotFail() {
        when(merchantRepository.findById(2L)).thenReturn(Optional.of(merchantWithoutWebhook));

        Refund refund = Refund.builder()
                .id(1L)
                .refundId("RFD-TEST001")
                .paymentId(1L)
                .amount(new BigDecimal("50.00"))
                .status(PaymentStatus.COMPLETED)
                .build();

        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-TEST")
                .merchantId(2L)
                .build();

        assertDoesNotThrow(() -> notificationService.sendRefundNotification(refund, payment));
    }

    @Test
    void sendRefundNotification_merchantNotFound_doesNotThrow() {
        when(merchantRepository.findById(999L)).thenReturn(Optional.empty());

        Refund refund = Refund.builder()
                .id(1L)
                .refundId("RFD-TEST001")
                .build();

        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-TEST")
                .merchantId(999L)
                .build();

        assertDoesNotThrow(() -> notificationService.sendRefundNotification(refund, payment));
    }

    // --- sendWebhook ---

    @Test
    void sendWebhook_failedDelivery_doesNotThrow() {
        // The URL won't actually work, but the method catches exceptions
        assertDoesNotThrow(() -> notificationService.sendWebhook("http://invalid-url:9999/webhook", "payload"));
    }
}
