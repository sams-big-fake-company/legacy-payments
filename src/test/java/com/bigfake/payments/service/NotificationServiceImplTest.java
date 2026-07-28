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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for NotificationServiceImpl.
 *
 * The RestTemplate is created internally by the service, so it is replaced with a
 * mock via reflection to keep these tests free of network I/O.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    private static final String WEBHOOK_URL = "https://merchant.example.com/hooks";

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private Payment payment;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationService, "restTemplate", restTemplate);

        payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-1")
                .merchantId(5L)
                .amount(new BigDecimal("42.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .customerEmail("customer@example.com")
                .build();
    }

    private Merchant merchantWithWebhook(String webhookUrl) {
        return Merchant.builder()
                .id(5L)
                .merchantCode("MERCH005")
                .businessName("Test Store")
                .isActive(true)
                .webhookUrl(webhookUrl)
                .build();
    }

    @Test
    void paymentNotificationPostsWebhookPayloadToMerchant() {
        when(merchantRepository.findById(5L)).thenReturn(Optional.of(merchantWithWebhook(WEBHOOK_URL)));

        notificationService.sendPaymentNotification(payment);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(restTemplate).postForEntity(eq(WEBHOOK_URL), captor.capture(), eq(String.class));
        assertEquals(Map.of(
                "event", "payment.updated",
                "transactionId", "TXN-1",
                "status", "COMPLETED",
                "amount", new BigDecimal("42.00"),
                "currency", "USD"), captor.getValue());
    }

    @Test
    void paymentNotificationSkipsWebhookWhenMerchantHasNoWebhookUrl() {
        when(merchantRepository.findById(5L)).thenReturn(Optional.of(merchantWithWebhook(null)));

        notificationService.sendPaymentNotification(payment);

        verifyNoInteractions(restTemplate);
    }

    @Test
    void paymentNotificationSkipsWebhookWhenMerchantIsUnknown() {
        when(merchantRepository.findById(5L)).thenReturn(Optional.empty());

        notificationService.sendPaymentNotification(payment);

        verifyNoInteractions(restTemplate);
    }

    @Test
    void paymentNotificationHandlesFailedPaymentsWithoutWebhook() {
        payment.setStatus(PaymentStatus.FAILED);
        when(merchantRepository.findById(5L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(payment));
    }

    @Test
    void paymentNotificationHandlesMissingCustomerEmail() {
        payment.setCustomerEmail(null);
        when(merchantRepository.findById(5L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(payment));
    }

    @Test
    void paymentNotificationHandlesEmptyCustomerEmail() {
        payment.setCustomerEmail("");
        when(merchantRepository.findById(5L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(payment));
    }

    @Test
    void paymentNotificationSendsNoEmailForNonTerminalStatus() {
        payment.setStatus(PaymentStatus.PENDING);
        when(merchantRepository.findById(5L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(payment));

        verifyNoInteractions(restTemplate);
    }

    @Test
    void refundNotificationSkipsWebhookWhenMerchantIsUnknown() {
        when(merchantRepository.findById(5L)).thenReturn(Optional.empty());
        Refund refund = Refund.builder().refundId("RFD-XYZ").amount(BigDecimal.ONE)
                .status(PaymentStatus.COMPLETED).build();

        notificationService.sendRefundNotification(refund, payment);

        verifyNoInteractions(restTemplate);
    }

    @Test
    void refundNotificationPostsRefundPayloadToMerchant() {
        when(merchantRepository.findById(5L)).thenReturn(Optional.of(merchantWithWebhook(WEBHOOK_URL)));
        Refund refund = Refund.builder()
                .refundId("RFD-XYZ")
                .paymentId(1L)
                .amount(new BigDecimal("10.00"))
                .status(PaymentStatus.COMPLETED)
                .build();

        notificationService.sendRefundNotification(refund, payment);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(restTemplate).postForEntity(eq(WEBHOOK_URL), captor.capture(), eq(String.class));
        assertEquals(Map.of(
                "event", "refund.processed",
                "refundId", "RFD-XYZ",
                "originalTransactionId", "TXN-1",
                "amount", new BigDecimal("10.00"),
                "status", "COMPLETED"), captor.getValue());
    }

    @Test
    void refundNotificationSkipsWebhookWhenMerchantHasNoWebhookUrl() {
        when(merchantRepository.findById(5L)).thenReturn(Optional.of(merchantWithWebhook(null)));
        Refund refund = Refund.builder().refundId("RFD-XYZ").amount(BigDecimal.ONE)
                .status(PaymentStatus.COMPLETED).build();

        notificationService.sendRefundNotification(refund, payment);

        verify(restTemplate, never()).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    void sendWebhookDelegatesToRestTemplate() {
        Map<String, Object> payload = Map.of("event", "ping");
        when(restTemplate.postForEntity(WEBHOOK_URL, payload, String.class)).thenReturn(ResponseEntity.ok("ok"));

        notificationService.sendWebhook(WEBHOOK_URL, payload);

        verify(restTemplate).postForEntity(WEBHOOK_URL, payload, String.class);
    }

    @Test
    void sendWebhookSwallowsDeliveryFailures() {
        Map<String, Object> payload = Map.of("event", "ping");
        when(restTemplate.postForEntity(WEBHOOK_URL, payload, String.class))
                .thenThrow(new RestClientException("connection refused"));

        assertDoesNotThrow(() -> notificationService.sendWebhook(WEBHOOK_URL, payload));
    }
}
