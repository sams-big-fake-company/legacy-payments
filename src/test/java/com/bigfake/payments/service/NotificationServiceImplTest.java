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
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

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
 * The RestTemplate is created internally by the service, so it is swapped for a mock
 * to keep the webhook boundary from making real HTTP calls.
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
                .merchantId(9L)
                .amount(new BigDecimal("25.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .customerEmail("customer@example.com")
                .build();

        merchant = Merchant.builder()
                .id(9L)
                .merchantCode("MERCH009")
                .businessName("Test Store")
                .isActive(true)
                .webhookUrl(WEBHOOK_URL)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturePostedPayload() {
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(restTemplate).postForEntity(eq(WEBHOOK_URL), payloadCaptor.capture(), eq(String.class));
        return (Map<String, Object>) payloadCaptor.getValue();
    }

    @Test
    void sendPaymentNotification_postsWebhookPayloadToMerchantUrl() {
        when(merchantRepository.findById(9L)).thenReturn(Optional.of(merchant));

        notificationService.sendPaymentNotification(payment);

        Map<String, Object> posted = capturePostedPayload();
        assertEquals("payment.updated", posted.get("event"));
        assertEquals("TXN-ABC123", posted.get("transactionId"));
        assertEquals("COMPLETED", posted.get("status"));
        assertEquals(new BigDecimal("25.00"), posted.get("amount"));
        assertEquals("USD", posted.get("currency"));
    }

    @Test
    void sendPaymentNotification_merchantNotFound_skipsWebhook() {
        when(merchantRepository.findById(9L)).thenReturn(Optional.empty());

        notificationService.sendPaymentNotification(payment);

        verifyNoInteractions(restTemplate);
    }

    @Test
    void sendPaymentNotification_merchantWithoutWebhookUrl_skipsWebhook() {
        merchant.setWebhookUrl(null);
        when(merchantRepository.findById(9L)).thenReturn(Optional.of(merchant));

        notificationService.sendPaymentNotification(payment);

        verifyNoInteractions(restTemplate);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"COMPLETED", "FAILED", "PENDING"})
    void sendPaymentNotification_emailBranchesDoNotAffectWebhookDelivery(PaymentStatus status) {
        payment.setStatus(status);
        when(merchantRepository.findById(9L)).thenReturn(Optional.of(merchant));

        notificationService.sendPaymentNotification(payment);

        assertEquals(status.name(), capturePostedPayload().get("status"));
    }

    @Test
    void sendPaymentNotification_withoutCustomerEmail_stillDeliversWebhook() {
        payment.setCustomerEmail(null);
        when(merchantRepository.findById(9L)).thenReturn(Optional.of(merchant));

        notificationService.sendPaymentNotification(payment);

        assertEquals("payment.updated", capturePostedPayload().get("event"));
    }

    @Test
    void sendPaymentNotification_withEmptyCustomerEmail_stillDeliversWebhook() {
        payment.setCustomerEmail("");
        when(merchantRepository.findById(9L)).thenReturn(Optional.of(merchant));

        notificationService.sendPaymentNotification(payment);

        assertEquals("payment.updated", capturePostedPayload().get("event"));
    }

    @Test
    void sendRefundNotification_postsRefundPayloadToMerchantUrl() {
        Refund refund = Refund.builder()
                .id(2L)
                .refundId("RFD-XYZ789")
                .paymentId(1L)
                .amount(new BigDecimal("10.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        when(merchantRepository.findById(9L)).thenReturn(Optional.of(merchant));

        notificationService.sendRefundNotification(refund, payment);

        Map<String, Object> posted = capturePostedPayload();
        assertEquals("refund.processed", posted.get("event"));
        assertEquals("RFD-XYZ789", posted.get("refundId"));
        assertEquals("TXN-ABC123", posted.get("originalTransactionId"));
        assertEquals(new BigDecimal("10.00"), posted.get("amount"));
        assertEquals("COMPLETED", posted.get("status"));
    }

    @Test
    void sendRefundNotification_merchantWithoutWebhookUrl_skipsWebhook() {
        merchant.setWebhookUrl(null);
        Refund refund = Refund.builder().refundId("RFD-XYZ789").amount(BigDecimal.ONE)
                .status(PaymentStatus.COMPLETED).build();
        when(merchantRepository.findById(9L)).thenReturn(Optional.of(merchant));

        notificationService.sendRefundNotification(refund, payment);

        verifyNoInteractions(restTemplate);
    }

    @Test
    void sendWebhook_delegatesToRestTemplate() {
        when(restTemplate.postForEntity(WEBHOOK_URL, Map.of("k", "v"), String.class))
                .thenReturn(ResponseEntity.ok("ok"));

        notificationService.sendWebhook(WEBHOOK_URL, Map.of("k", "v"));

        verify(restTemplate).postForEntity(WEBHOOK_URL, Map.of("k", "v"), String.class);
    }

    @Test
    void sendWebhook_deliveryFailureIsSwallowed() {
        when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
                .thenThrow(new RestClientException("connection refused"));

        notificationService.sendWebhook(WEBHOOK_URL, Map.of("k", "v"));

        verify(restTemplate).postForEntity(WEBHOOK_URL, Map.of("k", "v"), String.class);
        verify(merchantRepository, never()).findById(any());
    }
}
