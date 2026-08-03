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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
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
 * The service builds its own RestTemplate, so the HTTP boundary is replaced
 * through the field rather than through constructor injection.
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
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationService, "restTemplate", restTemplate);

        payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-ABC123")
                .merchantId(11L)
                .amount(new BigDecimal("25.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .customerEmail("customer@example.com")
                .build();

        merchant = Merchant.builder()
                .id(11L)
                .merchantCode("MERCH011")
                .businessName("Test Store")
                .isActive(true)
                .webhookUrl(WEBHOOK_URL)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturePostedPayload() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(restTemplate).postForEntity(eq(WEBHOOK_URL), captor.capture(), eq(String.class));
        return (Map<String, Object>) captor.getValue();
    }

    @Test
    void sendPaymentNotificationPostsThePaymentPayloadToTheMerchantWebhook() {
        when(merchantRepository.findById(11L)).thenReturn(Optional.of(merchant));
        when(restTemplate.postForEntity(eq(WEBHOOK_URL), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        notificationService.sendPaymentNotification(payment);

        Map<String, Object> body = capturePostedPayload();
        assertEquals("payment.updated", body.get("event"));
        assertEquals("TXN-ABC123", body.get("transactionId"));
        assertEquals("COMPLETED", body.get("status"));
        assertEquals(new BigDecimal("25.00"), body.get("amount"));
        assertEquals("USD", body.get("currency"));
    }

    @Test
    void sendPaymentNotificationSkipsTheWebhookWhenTheMerchantIsUnknown() {
        when(merchantRepository.findById(11L)).thenReturn(Optional.empty());

        notificationService.sendPaymentNotification(payment);

        verifyNoInteractions(restTemplate);
    }

    @Test
    void sendPaymentNotificationSkipsTheWebhookWhenTheMerchantHasNoWebhookUrl() {
        merchant.setWebhookUrl(null);
        when(merchantRepository.findById(11L)).thenReturn(Optional.of(merchant));

        notificationService.sendPaymentNotification(payment);

        verifyNoInteractions(restTemplate);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"COMPLETED", "FAILED", "PENDING"})
    void sendPaymentNotificationHandlesEveryStatusWithoutFailing(PaymentStatus status) {
        payment.setStatus(status);
        when(merchantRepository.findById(11L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(payment));
    }

    @Test
    void sendPaymentNotificationHandlesAMissingOrEmptyCustomerEmail() {
        when(merchantRepository.findById(11L)).thenReturn(Optional.empty());

        payment.setCustomerEmail(null);
        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(payment));

        payment.setCustomerEmail("");
        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(payment));
    }

    @Test
    void sendPaymentNotificationSwallowsWebhookDeliveryFailures() {
        when(merchantRepository.findById(11L)).thenReturn(Optional.of(merchant));
        when(restTemplate.postForEntity(eq(WEBHOOK_URL), any(), eq(String.class)))
                .thenThrow(new RestClientException("connection refused"));

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(payment));
    }

    @Test
    void sendRefundNotificationPostsTheRefundPayloadToTheMerchantWebhook() {
        Refund refund = Refund.builder()
                .id(2L)
                .refundId("RFD-XYZ789")
                .paymentId(1L)
                .amount(new BigDecimal("10.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        when(merchantRepository.findById(11L)).thenReturn(Optional.of(merchant));
        when(restTemplate.postForEntity(eq(WEBHOOK_URL), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        notificationService.sendRefundNotification(refund, payment);

        Map<String, Object> body = capturePostedPayload();
        assertEquals("refund.processed", body.get("event"));
        assertEquals("RFD-XYZ789", body.get("refundId"));
        assertEquals("TXN-ABC123", body.get("originalTransactionId"));
        assertEquals(new BigDecimal("10.00"), body.get("amount"));
        assertEquals("COMPLETED", body.get("status"));
    }

    @Test
    void sendRefundNotificationSkipsTheWebhookWhenTheMerchantHasNoWebhookUrl() {
        merchant.setWebhookUrl(null);
        Refund refund = Refund.builder()
                .refundId("RFD-XYZ789")
                .amount(new BigDecimal("10.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        when(merchantRepository.findById(11L)).thenReturn(Optional.of(merchant));

        notificationService.sendRefundNotification(refund, payment);

        verify(restTemplate, never()).postForEntity(any(String.class), any(), eq(String.class));
    }

    @Test
    void sendWebhookPostsThePayloadAsIs() {
        Map<String, Object> payload = Map.of("event", "custom.event");
        when(restTemplate.postForEntity(eq(WEBHOOK_URL), eq(payload), eq(String.class)))
                .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        notificationService.sendWebhook(WEBHOOK_URL, payload);

        verify(restTemplate).postForEntity(WEBHOOK_URL, payload, String.class);
    }

    @Test
    void sendWebhookNeverPropagatesTransportErrors() {
        when(restTemplate.postForEntity(eq(WEBHOOK_URL), any(), eq(String.class)))
                .thenThrow(new RestClientException("timeout"));

        assertDoesNotThrow(() -> notificationService.sendWebhook(WEBHOOK_URL, Map.of("event", "x")));
    }
}
