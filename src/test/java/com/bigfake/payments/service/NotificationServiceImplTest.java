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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NotificationServiceImpl.
 *
 * The service creates its own RestTemplate, so a mock is injected into the field to keep
 * these tests free of any real HTTP traffic.
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

    private Merchant merchant;
    private Payment payment;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationService, "restTemplate", restTemplate);

        merchant = Merchant.builder()
                .id(1L)
                .merchantCode("MERCH001")
                .businessName("Test Store")
                .isActive(true)
                .webhookUrl(WEBHOOK_URL)
                .build();

        payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-NOTIFY1")
                .merchantId(1L)
                .amount(new BigDecimal("75.50"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .customerEmail("buyer@example.com")
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturePayload() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(restTemplate).postForEntity(eq(WEBHOOK_URL), captor.capture(), eq(String.class));
        return (Map<String, Object>) captor.getValue();
    }

    @Test
    void sendPaymentNotification_postsTheMerchantWebhookPayload() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        notificationService.sendPaymentNotification(payment);

        Map<String, Object> payload = capturePayload();
        assertEquals("payment.updated", payload.get("event"));
        assertEquals("TXN-NOTIFY1", payload.get("transactionId"));
        assertEquals("COMPLETED", payload.get("status"));
        assertEquals(new BigDecimal("75.50"), payload.get("amount"));
        assertEquals("USD", payload.get("currency"));
    }

    @Test
    void sendPaymentNotification_skipsWebhookWhenMerchantHasNoWebhookUrl() {
        merchant.setWebhookUrl(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        notificationService.sendPaymentNotification(payment);

        verifyNoInteractions(restTemplate);
    }

    @Test
    void sendPaymentNotification_skipsWebhookWhenMerchantIsUnknown() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());

        notificationService.sendPaymentNotification(payment);

        verifyNoInteractions(restTemplate);
    }

    @Test
    void sendPaymentNotification_handlesFailedPaymentsWithoutAWebhook() {
        payment.setStatus(PaymentStatus.FAILED);
        merchant.setWebhookUrl(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        notificationService.sendPaymentNotification(payment);

        verifyNoInteractions(restTemplate);
    }

    @Test
    void sendPaymentNotification_worksWithoutACustomerEmail() {
        payment.setCustomerEmail(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        notificationService.sendPaymentNotification(payment);

        assertEquals("payment.updated", capturePayload().get("event"));
    }

    @Test
    void sendPaymentNotification_worksWithAnEmptyCustomerEmail() {
        payment.setCustomerEmail("");
        payment.setStatus(PaymentStatus.PENDING);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        notificationService.sendPaymentNotification(payment);

        assertEquals("PENDING", capturePayload().get("status"));
    }

    @Test
    void sendRefundNotification_postsTheRefundPayload() {
        Refund refund = Refund.builder()
                .id(9L)
                .refundId("RFD-ABC123")
                .paymentId(1L)
                .amount(new BigDecimal("25.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        notificationService.sendRefundNotification(refund, payment);

        Map<String, Object> payload = capturePayload();
        assertEquals("refund.processed", payload.get("event"));
        assertEquals("RFD-ABC123", payload.get("refundId"));
        assertEquals("TXN-NOTIFY1", payload.get("originalTransactionId"));
        assertEquals(new BigDecimal("25.00"), payload.get("amount"));
        assertEquals("COMPLETED", payload.get("status"));
    }

    @Test
    void sendRefundNotification_skipsWebhookWhenMerchantHasNoWebhookUrl() {
        merchant.setWebhookUrl(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        notificationService.sendRefundNotification(
                Refund.builder().refundId("RFD-ABC123").status(PaymentStatus.COMPLETED).build(), payment);

        verifyNoInteractions(restTemplate);
    }

    @Test
    void sendWebhook_swallowsDeliveryFailures() {
        when(restTemplate.postForEntity(eq(WEBHOOK_URL), any(), eq(String.class)))
                .thenThrow(new RestClientException("connection refused"));

        assertDoesNotThrow(() -> notificationService.sendWebhook(WEBHOOK_URL, Map.of("event", "ping")));
    }

    @Test
    void sendWebhook_postsThePayloadAsIs() {
        Map<String, Object> payload = Map.of("event", "ping");
        when(restTemplate.postForEntity(WEBHOOK_URL, payload, String.class)).thenReturn(ResponseEntity.ok("ok"));

        notificationService.sendWebhook(WEBHOOK_URL, payload);

        verify(restTemplate).postForEntity(WEBHOOK_URL, payload, String.class);
    }

    @Test
    void sendPaymentNotification_doesNotPropagateWebhookFailures() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        when(restTemplate.postForEntity(eq(WEBHOOK_URL), any(), eq(String.class)))
                .thenThrow(new RestClientException("timeout"));

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(payment));
    }
}
