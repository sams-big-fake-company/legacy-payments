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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for NotificationServiceImpl. The RestTemplate is replaced with a mock so
 * no HTTP calls leave the test JVM.
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
                .id(7L)
                .merchantCode("MERCH007")
                .businessName("Test Store")
                .isActive(true)
                .webhookUrl(WEBHOOK_URL)
                .build();

        payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-NOTIFY001")
                .merchantId(7L)
                .amount(new BigDecimal("25.50"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .customerEmail("customer@example.com")
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturePostedPayload() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(restTemplate).postForEntity(eq(WEBHOOK_URL), captor.capture(), eq(String.class));
        return (Map<String, Object>) captor.getValue();
    }

    @Test
    void sendPaymentNotification_postsWebhookPayloadToMerchantUrl() {
        when(merchantRepository.findById(7L)).thenReturn(Optional.of(merchant));
        when(restTemplate.postForEntity(eq(WEBHOOK_URL), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        notificationService.sendPaymentNotification(payment);

        Map<String, Object> body = capturePostedPayload();
        assertEquals("payment.updated", body.get("event"));
        assertEquals("TXN-NOTIFY001", body.get("transactionId"));
        assertEquals("COMPLETED", body.get("status"));
        assertEquals(new BigDecimal("25.50"), body.get("amount"));
        assertEquals("USD", body.get("currency"));
    }

    @Test
    void sendPaymentNotification_skipsWebhookWhenMerchantHasNoWebhookUrl() {
        merchant.setWebhookUrl(null);
        when(merchantRepository.findById(7L)).thenReturn(Optional.of(merchant));

        notificationService.sendPaymentNotification(payment);

        verifyNoInteractions(restTemplate);
    }

    @Test
    void sendPaymentNotification_skipsWebhookWhenMerchantIsMissing() {
        when(merchantRepository.findById(7L)).thenReturn(Optional.empty());

        notificationService.sendPaymentNotification(payment);

        verifyNoInteractions(restTemplate);
    }

    @Test
    void sendPaymentNotification_handlesFailedPaymentWithoutWebhook() {
        payment.setStatus(PaymentStatus.FAILED);
        merchant.setWebhookUrl(null);
        when(merchantRepository.findById(7L)).thenReturn(Optional.of(merchant));

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(payment));
    }

    @Test
    void sendPaymentNotification_handlesPendingPaymentWithoutCustomerEmail() {
        payment.setStatus(PaymentStatus.PENDING);
        payment.setCustomerEmail("");
        merchant.setWebhookUrl(null);
        when(merchantRepository.findById(7L)).thenReturn(Optional.of(merchant));

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(payment));
    }

    @Test
    void sendRefundNotification_postsRefundPayload() {
        Refund refund = Refund.builder()
                .id(3L)
                .refundId("RFD-ABC123")
                .paymentId(1L)
                .amount(new BigDecimal("10.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        when(merchantRepository.findById(7L)).thenReturn(Optional.of(merchant));
        when(restTemplate.postForEntity(eq(WEBHOOK_URL), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        notificationService.sendRefundNotification(refund, payment);

        Map<String, Object> body = capturePostedPayload();
        assertEquals("refund.processed", body.get("event"));
        assertEquals("RFD-ABC123", body.get("refundId"));
        assertEquals("TXN-NOTIFY001", body.get("originalTransactionId"));
        assertEquals(new BigDecimal("10.00"), body.get("amount"));
        assertEquals("COMPLETED", body.get("status"));
    }

    @Test
    void sendRefundNotification_skipsWebhookWhenMerchantHasNoWebhookUrl() {
        merchant.setWebhookUrl(null);
        Refund refund = Refund.builder().refundId("RFD-NOHOOK").status(PaymentStatus.COMPLETED).build();
        when(merchantRepository.findById(7L)).thenReturn(Optional.of(merchant));

        notificationService.sendRefundNotification(refund, payment);

        verify(restTemplate, never()).postForEntity(any(String.class), any(), eq(String.class));
    }

    @Test
    void sendWebhook_swallowsDeliveryFailures() {
        when(restTemplate.postForEntity(eq(WEBHOOK_URL), any(), eq(String.class)))
                .thenThrow(new RestClientException("connection refused"));

        assertDoesNotThrow(() -> notificationService.sendWebhook(WEBHOOK_URL, Map.of("event", "ping")));
    }
}
