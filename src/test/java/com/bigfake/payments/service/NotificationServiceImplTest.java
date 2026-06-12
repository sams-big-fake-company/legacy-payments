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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private Merchant merchantWithWebhook;
    private Payment payment;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationService, "restTemplate", restTemplate);

        merchantWithWebhook = Merchant.builder()
                .id(1L)
                .merchantCode("MERCH001")
                .businessName("Test Store")
                .webhookUrl("https://merchant.example.com/webhook")
                .isActive(true)
                .build();

        payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-NOTIFY1")
                .merchantId(1L)
                .amount(new BigDecimal("75.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .customerEmail("customer@example.com")
                .build();
    }

    @Test
    void sendPaymentNotification_sendsWebhookWithExpectedPayload() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithWebhook));

        notificationService.sendPaymentNotification(payment);

        verify(restTemplate).postForEntity(
                eq("https://merchant.example.com/webhook"),
                argThat(body -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) body;
                    return "payment.updated".equals(map.get("event"))
                            && "TXN-NOTIFY1".equals(map.get("transactionId"))
                            && "COMPLETED".equals(map.get("status"));
                }),
                eq(String.class));
    }

    @Test
    void sendPaymentNotification_noWebhookWhenMerchantHasNoUrl() {
        merchantWithWebhook.setWebhookUrl(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithWebhook));

        notificationService.sendPaymentNotification(payment);

        verifyNoInteractions(restTemplate);
    }

    @Test
    void sendPaymentNotification_noWebhookWhenMerchantNotFound() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());

        notificationService.sendPaymentNotification(payment);

        verifyNoInteractions(restTemplate);
    }

    @Test
    void sendPaymentNotification_failedPaymentStillNotifies() {
        payment.setStatus(PaymentStatus.FAILED);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithWebhook));

        notificationService.sendPaymentNotification(payment);

        verify(restTemplate).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    void sendPaymentNotification_noEmailDoesNotThrow() {
        payment.setCustomerEmail(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithWebhook));

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(payment));
    }

    @Test
    void sendRefundNotification_sendsWebhookWithExpectedPayload() {
        Refund refund = Refund.builder()
                .id(2L)
                .refundId("RFD-XYZ")
                .paymentId(1L)
                .amount(new BigDecimal("25.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchantWithWebhook));

        notificationService.sendRefundNotification(refund, payment);

        verify(restTemplate).postForEntity(
                eq("https://merchant.example.com/webhook"),
                argThat(body -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) body;
                    return "refund.processed".equals(map.get("event"))
                            && "RFD-XYZ".equals(map.get("refundId"))
                            && "TXN-NOTIFY1".equals(map.get("originalTransactionId"));
                }),
                eq(String.class));
    }

    @Test
    void sendRefundNotification_noWebhookWhenMerchantNotFound() {
        Refund refund = Refund.builder().id(2L).refundId("RFD-XYZ").paymentId(1L).build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());

        notificationService.sendRefundNotification(refund, payment);

        verifyNoInteractions(restTemplate);
    }

    @Test
    void sendWebhook_swallowsDeliveryFailure() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new RuntimeException("connection refused"));

        assertDoesNotThrow(() ->
                notificationService.sendWebhook("https://merchant.example.com/webhook", Map.of("event", "test")));
    }

    @Test
    void sendWebhook_postsPayloadToUrl() {
        Map<String, Object> payload = Map.of("event", "test");

        notificationService.sendWebhook("https://merchant.example.com/webhook", payload);

        verify(restTemplate).postForEntity("https://merchant.example.com/webhook", payload, String.class);
        assertEquals("test", payload.get("event"));
    }
}
