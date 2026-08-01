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
                .transactionId("TXN-TEST123")
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .customerEmail("customer@example.com")
                .build();

        testMerchant = Merchant.builder()
                .id(1L)
                .merchantCode("MERCH001")
                .businessName("Test Store")
                .webhookUrl("https://merchant.example.com/webhook")
                .isActive(true)
                .build();
    }

    @Test
    void sendPaymentNotification_merchantWithWebhook() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        notificationService.sendPaymentNotification(testPayment);

        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendPaymentNotification_merchantWithoutWebhook() {
        testMerchant.setWebhookUrl(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        notificationService.sendPaymentNotification(testPayment);

        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendPaymentNotification_merchantNotFound() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());

        notificationService.sendPaymentNotification(testPayment);

        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendPaymentNotification_completedPaymentWithEmail() {
        testPayment.setStatus(PaymentStatus.COMPLETED);
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());

        notificationService.sendPaymentNotification(testPayment);

        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendPaymentNotification_failedPaymentWithEmail() {
        testPayment.setStatus(PaymentStatus.FAILED);
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());

        notificationService.sendPaymentNotification(testPayment);

        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendPaymentNotification_noCustomerEmail() {
        testPayment.setCustomerEmail(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());

        notificationService.sendPaymentNotification(testPayment);

        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendPaymentNotification_emptyCustomerEmail() {
        testPayment.setCustomerEmail("");
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());

        notificationService.sendPaymentNotification(testPayment);

        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendRefundNotification_merchantWithWebhook() {
        Refund refund = Refund.builder()
                .id(1L)
                .refundId("RFD-123")
                .paymentId(1L)
                .amount(new BigDecimal("50.00"))
                .status(PaymentStatus.COMPLETED)
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        notificationService.sendRefundNotification(refund, testPayment);

        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendRefundNotification_merchantWithoutWebhook() {
        testMerchant.setWebhookUrl(null);
        Refund refund = Refund.builder()
                .id(1L)
                .refundId("RFD-123")
                .paymentId(1L)
                .amount(new BigDecimal("50.00"))
                .status(PaymentStatus.COMPLETED)
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        notificationService.sendRefundNotification(refund, testPayment);

        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendRefundNotification_merchantNotFound() {
        Refund refund = Refund.builder()
                .id(1L)
                .refundId("RFD-123")
                .paymentId(1L)
                .amount(new BigDecimal("50.00"))
                .status(PaymentStatus.COMPLETED)
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());

        notificationService.sendRefundNotification(refund, testPayment);

        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendWebhook_invalidUrl_doesNotThrow() {
        assertDoesNotThrow(() ->
                notificationService.sendWebhook("http://invalid.local/webhook", "test"));
    }
}
