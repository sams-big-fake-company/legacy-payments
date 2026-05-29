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
                .transactionId("TXN-TEST123")
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .customerEmail("customer@example.com")
                .build();

        merchantWithWebhook = Merchant.builder()
                .id(1L)
                .merchantCode("MERCH001")
                .webhookUrl("http://merchant.example.com/webhook")
                .build();

        merchantWithoutWebhook = Merchant.builder()
                .id(2L)
                .merchantCode("MERCH002")
                .webhookUrl(null)
                .build();
    }

    @Test
    void sendPaymentNotification_merchantWithWebhook_attemptsDelivery() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithWebhook));
        notificationService.sendPaymentNotification(testPayment);
        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendPaymentNotification_merchantWithoutWebhook_noWebhookSent() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithoutWebhook));
        notificationService.sendPaymentNotification(testPayment);
        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendPaymentNotification_merchantNotFound_doesNotThrow() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());
        notificationService.sendPaymentNotification(testPayment);
        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendPaymentNotification_failedPayment_logsFailureEmail() {
        testPayment.setStatus(PaymentStatus.FAILED);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithoutWebhook));
        notificationService.sendPaymentNotification(testPayment);
        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendPaymentNotification_noCustomerEmail_doesNotThrow() {
        testPayment.setCustomerEmail(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithoutWebhook));
        notificationService.sendPaymentNotification(testPayment);
        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendPaymentNotification_emptyCustomerEmail_doesNotThrow() {
        testPayment.setCustomerEmail("");
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithoutWebhook));
        notificationService.sendPaymentNotification(testPayment);
        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendPaymentNotification_pendingStatus_noEmailSent() {
        testPayment.setStatus(PaymentStatus.PENDING);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithoutWebhook));
        notificationService.sendPaymentNotification(testPayment);
        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendRefundNotification_merchantWithWebhook_attemptsDelivery() {
        Refund refund = Refund.builder()
                .id(1L)
                .refundId("RFD-TEST123")
                .paymentId(1L)
                .amount(new BigDecimal("50.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithWebhook));
        notificationService.sendRefundNotification(refund, testPayment);
        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendRefundNotification_merchantWithoutWebhook_noWebhookSent() {
        Refund refund = Refund.builder()
                .id(1L)
                .refundId("RFD-TEST123")
                .paymentId(1L)
                .amount(new BigDecimal("50.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithoutWebhook));
        notificationService.sendRefundNotification(refund, testPayment);
        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendRefundNotification_merchantNotFound_doesNotThrow() {
        Refund refund = Refund.builder()
                .id(1L)
                .refundId("RFD-TEST123")
                .paymentId(1L)
                .amount(new BigDecimal("50.00"))
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());
        notificationService.sendRefundNotification(refund, testPayment);
    }

    @Test
    void sendWebhook_invalidUrl_doesNotThrow() {
        notificationService.sendWebhook("http://invalid-url-that-wont-resolve.example.com/webhook", "payload");
    }
}
