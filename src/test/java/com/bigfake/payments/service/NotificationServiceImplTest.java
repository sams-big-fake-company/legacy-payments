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

    private Payment completedPayment;
    private Merchant merchantWithWebhook;
    private Merchant merchantWithoutWebhook;

    @BeforeEach
    void setUp() {
        completedPayment = Payment.builder()
                .id(1L)
                .transactionId("TXN-NOTIFY001")
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
                .businessName("Test Store")
                .webhookUrl("https://merchant.example.com/webhook")
                .isActive(true)
                .build();

        merchantWithoutWebhook = Merchant.builder()
                .id(2L)
                .merchantCode("MERCH002")
                .businessName("Store No Webhook")
                .isActive(true)
                .build();
    }

    @Test
    void sendPaymentNotification_merchantNotFound_doesNotThrow() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(completedPayment));
    }

    @Test
    void sendPaymentNotification_merchantWithoutWebhook_doesNotThrow() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithoutWebhook));

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(completedPayment));
    }

    @Test
    void sendPaymentNotification_merchantWithWebhook_sendsWebhook() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithWebhook));

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(completedPayment));
    }

    @Test
    void sendPaymentNotification_failedPayment_doesNotThrow() {
        completedPayment.setStatus(PaymentStatus.FAILED);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithoutWebhook));

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(completedPayment));
    }

    @Test
    void sendPaymentNotification_noCustomerEmail_doesNotThrow() {
        completedPayment.setCustomerEmail(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithoutWebhook));

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(completedPayment));
    }

    @Test
    void sendPaymentNotification_emptyCustomerEmail_doesNotThrow() {
        completedPayment.setCustomerEmail("");
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithoutWebhook));

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(completedPayment));
    }

    @Test
    void sendRefundNotification_merchantWithWebhook() {
        Refund refund = Refund.builder()
                .id(1L)
                .refundId("RFD-NOTIFY001")
                .paymentId(1L)
                .amount(new BigDecimal("50.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithWebhook));

        assertDoesNotThrow(() -> notificationService.sendRefundNotification(refund, completedPayment));
    }

    @Test
    void sendRefundNotification_merchantWithoutWebhook() {
        Refund refund = Refund.builder()
                .id(1L)
                .refundId("RFD-NOTIFY002")
                .paymentId(1L)
                .amount(new BigDecimal("50.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithoutWebhook));

        assertDoesNotThrow(() -> notificationService.sendRefundNotification(refund, completedPayment));
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

        assertDoesNotThrow(() -> notificationService.sendRefundNotification(refund, completedPayment));
    }

    @Test
    void sendWebhook_invalidUrl_doesNotThrow() {
        assertDoesNotThrow(() -> notificationService.sendWebhook("http://invalid-host-that-does-not-exist.xyz/webhook", "payload"));
    }
}
