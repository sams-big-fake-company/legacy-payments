package com.bigfake.payments.service;

import com.bigfake.payments.model.entity.Merchant;
import com.bigfake.payments.model.entity.Payment;
import com.bigfake.payments.model.entity.Refund;
import com.bigfake.payments.model.enums.PaymentStatus;
import com.bigfake.payments.repository.MerchantRepository;
import com.bigfake.payments.service.impl.NotificationServiceImpl;
import com.bigfake.payments.testsupport.TestFixtures;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NotificationServiceImpl}. The {@link RestTemplate} the
 * service creates internally is replaced with a mock so no HTTP calls are made.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    private static final String WEBHOOK_URL = "https://merchant.example/hooks/payments";

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @BeforeEach
    void replaceRestTemplate() {
        ReflectionTestUtils.setField(notificationService, "restTemplate", restTemplate);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturePayload() {
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(restTemplate).postForEntity(eq(WEBHOOK_URL), payload.capture(), eq(String.class));
        return (Map<String, Object>) payload.getValue();
    }

    @Test
    void sendsPaymentWebhookToTheMerchantEndpoint() {
        Merchant merchant = TestFixtures.merchant().webhookUrl(WEBHOOK_URL).build();
        when(merchantRepository.findById(TestFixtures.MERCHANT_ID)).thenReturn(Optional.of(merchant));
        Payment payment = TestFixtures.payment().build();

        notificationService.sendPaymentNotification(payment);

        Map<String, Object> payload = capturePayload();
        assertEquals("payment.updated", payload.get("event"));
        assertEquals("TXN-0123456789ABCDEF", payload.get("transactionId"));
        assertEquals("COMPLETED", payload.get("status"));
        assertEquals(new BigDecimal("99.99"), payload.get("amount"));
        assertEquals("USD", payload.get("currency"));
    }

    @Test
    void skipsThePaymentWebhookWhenTheMerchantHasNoWebhookUrl() {
        when(merchantRepository.findById(TestFixtures.MERCHANT_ID))
                .thenReturn(Optional.of(TestFixtures.merchant().webhookUrl(null).build()));

        notificationService.sendPaymentNotification(TestFixtures.payment().build());

        verifyNoInteractions(restTemplate);
    }

    @Test
    void skipsThePaymentWebhookWhenTheMerchantIsUnknown() {
        when(merchantRepository.findById(TestFixtures.MERCHANT_ID)).thenReturn(Optional.empty());

        notificationService.sendPaymentNotification(TestFixtures.payment().build());

        verifyNoInteractions(restTemplate);
    }

    @ParameterizedTest(name = "notification for a {0} payment does not fail")
    @EnumSource(PaymentStatus.class)
    void handlesEveryPaymentStatusForCustomerEmails(PaymentStatus status) {
        when(merchantRepository.findById(TestFixtures.MERCHANT_ID))
                .thenReturn(Optional.of(TestFixtures.merchant().webhookUrl(null).build()));
        Payment payment = TestFixtures.payment().status(status).build();

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(payment));
    }

    @Test
    void handlesPaymentsWithoutACustomerEmail() {
        when(merchantRepository.findById(TestFixtures.MERCHANT_ID))
                .thenReturn(Optional.of(TestFixtures.merchant().webhookUrl(null).build()));

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(
                TestFixtures.payment().customerEmail("").build()));
        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(
                TestFixtures.payment().customerEmail(null).build()));
    }

    @Test
    void sendsRefundWebhookToTheMerchantEndpoint() {
        when(merchantRepository.findById(TestFixtures.MERCHANT_ID))
                .thenReturn(Optional.of(TestFixtures.merchant().webhookUrl(WEBHOOK_URL).build()));
        Refund refund = TestFixtures.refund().build();

        notificationService.sendRefundNotification(refund, TestFixtures.payment().build());

        Map<String, Object> payload = capturePayload();
        assertEquals("refund.processed", payload.get("event"));
        assertEquals("RFD-ABCDEF123456", payload.get("refundId"));
        assertEquals("TXN-0123456789ABCDEF", payload.get("originalTransactionId"));
        assertEquals(new BigDecimal("10.00"), payload.get("amount"));
        assertEquals("COMPLETED", payload.get("status"));
    }

    @Test
    void skipsTheRefundWebhookWhenTheMerchantHasNoWebhookUrl() {
        when(merchantRepository.findById(TestFixtures.MERCHANT_ID))
                .thenReturn(Optional.of(TestFixtures.merchant().webhookUrl(null).build()));

        notificationService.sendRefundNotification(TestFixtures.refund().build(),
                TestFixtures.payment().build());

        verify(restTemplate, never()).postForEntity(any(String.class), any(), eq(String.class));
    }

    @Test
    void webhookDeliveryFailuresAreSwallowed() {
        when(restTemplate.postForEntity(eq(WEBHOOK_URL), any(), eq(String.class)))
                .thenThrow(new RestClientException("connection refused"));

        assertDoesNotThrow(() -> notificationService.sendWebhook(WEBHOOK_URL, Map.of("event", "ping")));
    }

    @Test
    void webhookPostsThePayloadAsIs() {
        Map<String, Object> payload = Map.of("event", "ping");
        when(restTemplate.postForEntity(eq(WEBHOOK_URL), eq(payload), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        notificationService.sendWebhook(WEBHOOK_URL, payload);

        verify(restTemplate).postForEntity(eq(WEBHOOK_URL), eq(payload), eq(String.class));
    }
}
