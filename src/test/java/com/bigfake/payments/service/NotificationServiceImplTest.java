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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for NotificationServiceImpl.
 *
 * The RestTemplate is created inline by the service, so it is swapped for a mock
 * to keep these tests off the network.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    private static final String WEBHOOK_URL = "https://merchant.example.com/hooks/payments";

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
                .transactionId("TXN-NOTIFY1")
                .merchantId(9L)
                .amount(new BigDecimal("75.50"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .customerEmail("buyer@example.com")
                .build();
    }

    private static Merchant merchantWithWebhook(String webhookUrl) {
        return Merchant.builder()
                .id(9L)
                .merchantCode("MERCH009")
                .businessName("Webhook Store")
                .isActive(true)
                .webhookUrl(webhookUrl)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturePayload() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(restTemplate).postForEntity(eq(WEBHOOK_URL), captor.capture(), eq(String.class));
        return (Map<String, Object>) captor.getValue();
    }

    @Test
    void sendPaymentNotification_merchantWithWebhook_postsPaymentPayload() {
        when(merchantRepository.findById(9L)).thenReturn(Optional.of(merchantWithWebhook(WEBHOOK_URL)));
        when(restTemplate.postForEntity(eq(WEBHOOK_URL), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        notificationService.sendPaymentNotification(payment);

        Map<String, Object> body = capturePayload();
        assertEquals("payment.updated", body.get("event"));
        assertEquals("TXN-NOTIFY1", body.get("transactionId"));
        assertEquals("COMPLETED", body.get("status"));
        assertEquals(new BigDecimal("75.50"), body.get("amount"));
        assertEquals("USD", body.get("currency"));
    }

    @Test
    void sendPaymentNotification_merchantWithoutWebhookUrl_skipsWebhook() {
        when(merchantRepository.findById(9L)).thenReturn(Optional.of(merchantWithWebhook(null)));

        notificationService.sendPaymentNotification(payment);

        verifyNoInteractions(restTemplate);
    }

    @Test
    void sendPaymentNotification_unknownMerchant_skipsWebhook() {
        when(merchantRepository.findById(9L)).thenReturn(Optional.empty());

        notificationService.sendPaymentNotification(payment);

        verifyNoInteractions(restTemplate);
    }

    @Test
    void sendPaymentNotification_failedPaymentWithEmail_stillSkipsWebhookWhenNoUrl() {
        payment.setStatus(PaymentStatus.FAILED);
        when(merchantRepository.findById(9L)).thenReturn(Optional.empty());

        notificationService.sendPaymentNotification(payment);

        verifyNoInteractions(restTemplate);
    }

    @Test
    void sendPaymentNotification_pendingPaymentWithoutEmail_doesNotFail() {
        payment.setStatus(PaymentStatus.PENDING);
        payment.setCustomerEmail(null);
        when(merchantRepository.findById(9L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(payment));
    }

    @Test
    void sendPaymentNotification_emptyCustomerEmail_doesNotFail() {
        payment.setCustomerEmail("");
        when(merchantRepository.findById(9L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(payment));
    }

    @Test
    void sendRefundNotification_merchantWithWebhook_postsRefundPayload() {
        Refund refund = Refund.builder()
                .id(5L)
                .refundId("RFD-XYZ")
                .paymentId(1L)
                .amount(new BigDecimal("25.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        when(merchantRepository.findById(9L)).thenReturn(Optional.of(merchantWithWebhook(WEBHOOK_URL)));
        when(restTemplate.postForEntity(eq(WEBHOOK_URL), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        notificationService.sendRefundNotification(refund, payment);

        Map<String, Object> body = capturePayload();
        assertEquals("refund.processed", body.get("event"));
        assertEquals("RFD-XYZ", body.get("refundId"));
        assertEquals("TXN-NOTIFY1", body.get("originalTransactionId"));
        assertEquals(new BigDecimal("25.00"), body.get("amount"));
        assertEquals("COMPLETED", body.get("status"));
    }

    @Test
    void sendRefundNotification_merchantWithoutWebhookUrl_skipsWebhook() {
        Refund refund = Refund.builder().refundId("RFD-XYZ").amount(BigDecimal.ONE)
                .status(PaymentStatus.COMPLETED).build();
        when(merchantRepository.findById(9L)).thenReturn(Optional.of(merchantWithWebhook(null)));

        notificationService.sendRefundNotification(refund, payment);

        verify(restTemplate, never()).postForEntity(any(String.class), any(), eq(String.class));
    }

    @Test
    void sendWebhook_deliveryFailure_isSwallowed() {
        when(restTemplate.postForEntity(eq(WEBHOOK_URL), any(), eq(String.class)))
                .thenThrow(new RestClientException("connection refused"));

        assertDoesNotThrow(() -> notificationService.sendWebhook(WEBHOOK_URL, Map.of("event", "ping")));
    }

    @Test
    void sendWebhook_successfulDelivery_postsPayloadAsIs() {
        Map<String, Object> payload = Map.of("event", "ping");
        when(restTemplate.postForEntity(eq(WEBHOOK_URL), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        notificationService.sendWebhook(WEBHOOK_URL, payload);

        verify(restTemplate).postForEntity(WEBHOOK_URL, payload, String.class);
    }
}
