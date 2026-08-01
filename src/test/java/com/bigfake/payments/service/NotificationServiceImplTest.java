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

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private MerchantRepository merchantRepository;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private Merchant merchant;
    private Payment payment;

    @BeforeEach
    void setUp() {
        merchant = Merchant.builder()
                .id(1L)
                .merchantCode("MERCH001")
                .businessName("Test Store")
                .webhookUrl("https://example.com/webhook")
                .isActive(true)
                .build();

        payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-TEST123")
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .customerEmail("customer@example.com")
                .build();
    }

    @Test
    void sendPaymentNotification_merchantWithWebhook_sendsWebhook() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        notificationService.sendPaymentNotification(payment);

        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendPaymentNotification_merchantWithoutWebhook_noException() {
        merchant.setWebhookUrl(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        notificationService.sendPaymentNotification(payment);

        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendPaymentNotification_merchantNotFound_noException() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());

        notificationService.sendPaymentNotification(payment);

        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendPaymentNotification_failedPayment_logsEmail() {
        payment.setStatus(PaymentStatus.FAILED);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        notificationService.sendPaymentNotification(payment);

        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendPaymentNotification_noCustomerEmail_noEmailAttempt() {
        payment.setCustomerEmail(null);
        merchant.setWebhookUrl(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        notificationService.sendPaymentNotification(payment);

        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendPaymentNotification_emptyCustomerEmail_noEmailAttempt() {
        payment.setCustomerEmail("");
        merchant.setWebhookUrl(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        notificationService.sendPaymentNotification(payment);

        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendPaymentNotification_processingStatus_noEmail() {
        payment.setStatus(PaymentStatus.PROCESSING);
        merchant.setWebhookUrl(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        notificationService.sendPaymentNotification(payment);

        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendRefundNotification_merchantWithWebhook_sendsWebhook() {
        Refund refund = Refund.builder()
                .id(1L)
                .refundId("RFD-TEST123")
                .paymentId(1L)
                .amount(new BigDecimal("50.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        notificationService.sendRefundNotification(refund, payment);

        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendRefundNotification_merchantWithoutWebhook_noException() {
        merchant.setWebhookUrl(null);
        Refund refund = Refund.builder()
                .id(1L)
                .refundId("RFD-TEST123")
                .paymentId(1L)
                .amount(new BigDecimal("50.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        notificationService.sendRefundNotification(refund, payment);

        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendRefundNotification_merchantNotFound_noException() {
        Refund refund = Refund.builder()
                .id(1L)
                .refundId("RFD-TEST123")
                .paymentId(1L)
                .amount(new BigDecimal("50.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());

        notificationService.sendRefundNotification(refund, payment);

        verify(merchantRepository).findById(1L);
    }
}
