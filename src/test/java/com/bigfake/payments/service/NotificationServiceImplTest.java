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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for NotificationServiceImpl.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    private static final String WEBHOOK_URL = "https://merchant.example.com/hooks";

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private RestTemplate restTemplate;

    private NotificationServiceImpl notificationService;

    private Payment payment;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationServiceImpl();
        ReflectionTestUtils.setField(notificationService, "merchantRepository", merchantRepository);
        ReflectionTestUtils.setField(notificationService, "restTemplate", restTemplate);

        payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-123")
                .merchantId(9L)
                .amount(new BigDecimal("75.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .customerEmail("buyer@example.com")
                .build();
    }

    private static Merchant merchantWithWebhook(String webhookUrl) {
        return Merchant.builder()
                .id(9L)
                .merchantCode("MERCH009")
                .businessName("Test Store")
                .isActive(true)
                .webhookUrl(webhookUrl)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturedPayload() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(restTemplate).postForEntity(eq(WEBHOOK_URL), captor.capture(), eq(String.class));
        return (Map<String, Object>) captor.getValue();
    }

    @Test
    void sendPaymentNotification_merchantWithWebhook_postsPaymentUpdatedPayload() {
        when(merchantRepository.findById(9L)).thenReturn(Optional.of(merchantWithWebhook(WEBHOOK_URL)));

        notificationService.sendPaymentNotification(payment);

        Map<String, Object> payload = capturedPayload();
        assertEquals("payment.updated", payload.get("event"));
        assertEquals("TXN-123", payload.get("transactionId"));
        assertEquals("COMPLETED", payload.get("status"));
        assertEquals(new BigDecimal("75.00"), payload.get("amount"));
        assertEquals("USD", payload.get("currency"));
    }

    @Test
    void sendPaymentNotification_merchantWithoutWebhook_doesNotPost() {
        when(merchantRepository.findById(9L)).thenReturn(Optional.of(merchantWithWebhook(null)));

        notificationService.sendPaymentNotification(payment);

        verifyNoInteractions(restTemplate);
    }

    @Test
    void sendPaymentNotification_unknownMerchant_doesNotPost() {
        when(merchantRepository.findById(9L)).thenReturn(Optional.empty());

        notificationService.sendPaymentNotification(payment);

        verifyNoInteractions(restTemplate);
    }

    @Test
    void sendPaymentNotification_failedPaymentWithEmail_stillNotifiesMerchant() {
        payment.setStatus(PaymentStatus.FAILED);
        when(merchantRepository.findById(9L)).thenReturn(Optional.of(merchantWithWebhook(WEBHOOK_URL)));

        notificationService.sendPaymentNotification(payment);

        assertEquals("FAILED", capturedPayload().get("status"));
    }

    @Test
    void sendPaymentNotification_pendingPaymentWithoutEmail_postsWebhookOnly() {
        payment.setStatus(PaymentStatus.PENDING);
        payment.setCustomerEmail(null);
        when(merchantRepository.findById(9L)).thenReturn(Optional.of(merchantWithWebhook(WEBHOOK_URL)));

        notificationService.sendPaymentNotification(payment);

        assertEquals("PENDING", capturedPayload().get("status"));
    }

    @Test
    void sendPaymentNotification_emptyCustomerEmail_isHandled() {
        payment.setCustomerEmail("");
        when(merchantRepository.findById(9L)).thenReturn(Optional.of(merchantWithWebhook(WEBHOOK_URL)));

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(payment));
    }

    @Test
    void sendRefundNotification_merchantWithWebhook_postsRefundProcessedPayload() {
        when(merchantRepository.findById(9L)).thenReturn(Optional.of(merchantWithWebhook(WEBHOOK_URL)));
        Refund refund = Refund.builder()
                .refundId("RFD-ABC")
                .paymentId(1L)
                .amount(new BigDecimal("25.00"))
                .status(PaymentStatus.COMPLETED)
                .build();

        notificationService.sendRefundNotification(refund, payment);

        Map<String, Object> payload = capturedPayload();
        assertEquals("refund.processed", payload.get("event"));
        assertEquals("RFD-ABC", payload.get("refundId"));
        assertEquals("TXN-123", payload.get("originalTransactionId"));
        assertEquals(new BigDecimal("25.00"), payload.get("amount"));
        assertEquals("COMPLETED", payload.get("status"));
    }

    @Test
    void sendRefundNotification_merchantWithoutWebhook_doesNotPost() {
        when(merchantRepository.findById(9L)).thenReturn(Optional.of(merchantWithWebhook(null)));

        notificationService.sendRefundNotification(Refund.builder()
                .refundId("RFD-ABC")
                .amount(new BigDecimal("25.00"))
                .status(PaymentStatus.COMPLETED)
                .build(), payment);

        verify(restTemplate, never()).postForEntity(any(String.class), any(), eq(String.class));
    }

    @Test
    void sendWebhook_deliversPayloadToUrl() {
        Map<String, Object> payload = Map.of("event", "ping");

        notificationService.sendWebhook(WEBHOOK_URL, payload);

        verify(restTemplate).postForEntity(WEBHOOK_URL, payload, String.class);
    }

    @Test
    void sendWebhook_deliveryFailure_isSwallowed() {
        when(restTemplate.postForEntity(eq(WEBHOOK_URL), any(), eq(String.class)))
                .thenThrow(new RestClientException("connection refused"));

        assertDoesNotThrow(() -> notificationService.sendWebhook(WEBHOOK_URL, Map.of("event", "ping")));
    }

    @Test
    void sendPaymentNotification_webhookFailure_doesNotPropagate() {
        when(merchantRepository.findById(9L)).thenReturn(Optional.of(merchantWithWebhook(WEBHOOK_URL)));
        when(restTemplate.postForEntity(eq(WEBHOOK_URL), any(), eq(String.class)))
                .thenThrow(new RestClientException("timeout"));

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(payment));
    }
}
