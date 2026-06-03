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

    private Payment completedPayment;
    private Merchant merchantWithWebhook;
    private Merchant merchantWithoutWebhook;

    @BeforeEach
    void setUp() {
        completedPayment = Payment.builder()
                .id(1L)
                .transactionId("TXN-TEST123")
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .customerEmail("customer@example.com")
                .build();

        merchantWithWebhook = Merchant.builder()
                .id(1L)
                .merchantCode("MERCH001")
                .businessName("Test Store")
                .isActive(true)
                .webhookUrl("https://example.com/webhook")
                .build();

        merchantWithoutWebhook = Merchant.builder()
                .id(2L)
                .merchantCode("MERCH002")
                .businessName("No Webhook Store")
                .isActive(true)
                .webhookUrl(null)
                .build();
    }

    @Test
    void sendPaymentNotification_merchantNotFound_noException() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());

        notificationService.sendPaymentNotification(completedPayment);

        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendPaymentNotification_merchantWithoutWebhook_noWebhookSent() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithoutWebhook));

        notificationService.sendPaymentNotification(completedPayment);

        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendPaymentNotification_noCustomerEmail_noEmailSent() {
        completedPayment.setCustomerEmail(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());

        notificationService.sendPaymentNotification(completedPayment);

        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendPaymentNotification_emptyCustomerEmail_noEmailSent() {
        completedPayment.setCustomerEmail("");
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());

        notificationService.sendPaymentNotification(completedPayment);

        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendPaymentNotification_failedPayment_logsFailureEmail() {
        completedPayment.setStatus(PaymentStatus.FAILED);
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());

        notificationService.sendPaymentNotification(completedPayment);

        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendPaymentNotification_pendingPayment_noEmailSent() {
        completedPayment.setStatus(PaymentStatus.PENDING);
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());

        notificationService.sendPaymentNotification(completedPayment);

        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendRefundNotification_merchantNotFound_noException() {
        Refund refund = Refund.builder()
                .id(1L)
                .refundId("RFD-123")
                .paymentId(1L)
                .amount(new BigDecimal("25.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());

        notificationService.sendRefundNotification(refund, completedPayment);

        verify(merchantRepository).findById(1L);
    }

    @Test
    void sendRefundNotification_merchantWithoutWebhook_noWebhookSent() {
        Refund refund = Refund.builder()
                .id(1L)
                .refundId("RFD-123")
                .paymentId(1L)
                .amount(new BigDecimal("25.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithoutWebhook));

        notificationService.sendRefundNotification(refund, completedPayment);

        verify(merchantRepository).findById(1L);
    }
}
