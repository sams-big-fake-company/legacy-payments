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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.mockito.Mockito.when;

/**
 * Unit tests for NotificationServiceImpl.
 *
 * The service creates its own RestTemplate (PAY-4105), so the outbound HTTP
 * boundary is stubbed with MockRestServiceServer - no real network calls are made.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    private static final String WEBHOOK_URL = "https://merchant.example.com/hooks/payments";

    @Mock
    private MerchantRepository merchantRepository;

    private NotificationServiceImpl notificationService;
    private MockRestServiceServer mockServer;

    private Payment payment;
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationServiceImpl();
        ReflectionTestUtils.setField(notificationService, "merchantRepository", merchantRepository);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(notificationService, "restTemplate");
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();

        merchant = Merchant.builder()
                .id(1L)
                .merchantCode("MERCH001")
                .businessName("Test Store")
                .isActive(true)
                .webhookUrl(WEBHOOK_URL)
                .build();

        payment = Payment.builder()
                .id(7L)
                .transactionId("TXN-NOTIFY001")
                .merchantId(1L)
                .amount(new BigDecimal("25.50"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .customerEmail("customer@example.com")
                .build();
    }

    @Test
    void sendPaymentNotification_postsWebhookPayloadToMerchantUrl() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        mockServer.expect(requestTo(WEBHOOK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.event").value("payment.updated"))
                .andExpect(jsonPath("$.transactionId").value("TXN-NOTIFY001"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(25.50))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andRespond(withSuccess("{}", org.springframework.http.MediaType.APPLICATION_JSON));

        notificationService.sendPaymentNotification(payment);

        mockServer.verify();
    }

    @Test
    void sendPaymentNotification_skipsWebhookWhenMerchantIsUnknown() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());

        notificationService.sendPaymentNotification(payment);

        mockServer.verify(); // no request expected, any request would fail the test
    }

    @Test
    void sendPaymentNotification_skipsWebhookWhenMerchantHasNoWebhookUrl() {
        merchant.setWebhookUrl(null);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        notificationService.sendPaymentNotification(payment);

        mockServer.verify();
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"COMPLETED", "FAILED", "PENDING"})
    void sendPaymentNotification_handlesEveryStatusWhenCustomerEmailIsPresent(PaymentStatus status) {
        merchant.setWebhookUrl(null);
        payment.setStatus(status);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(payment));
    }

    @Test
    void sendPaymentNotification_handlesMissingCustomerEmail() {
        merchant.setWebhookUrl(null);
        payment.setCustomerEmail("");
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(payment));

        payment.setCustomerEmail(null);
        assertDoesNotThrow(() -> notificationService.sendPaymentNotification(payment));
    }

    @Test
    void sendRefundNotification_postsRefundPayloadToMerchantUrl() {
        Refund refund = Refund.builder()
                .id(3L)
                .refundId("RFD-XYZ123")
                .paymentId(7L)
                .amount(new BigDecimal("10.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        mockServer.expect(requestTo(WEBHOOK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.event").value("refund.processed"))
                .andExpect(jsonPath("$.refundId").value("RFD-XYZ123"))
                .andExpect(jsonPath("$.originalTransactionId").value("TXN-NOTIFY001"))
                .andExpect(jsonPath("$.amount").value(10.00))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andRespond(withSuccess("{}", org.springframework.http.MediaType.APPLICATION_JSON));

        notificationService.sendRefundNotification(refund, payment);

        mockServer.verify();
    }

    @Test
    void sendRefundNotification_skipsWebhookWhenMerchantIsUnknown() {
        Refund refund = Refund.builder().refundId("RFD-XYZ123").amount(BigDecimal.ONE)
                .status(PaymentStatus.COMPLETED).build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());

        notificationService.sendRefundNotification(refund, payment);

        mockServer.verify();
    }

    @Test
    void sendWebhook_swallowsDeliveryFailures() {
        mockServer.expect(requestTo(WEBHOOK_URL)).andRespond(withServerError());

        assertDoesNotThrow(() -> notificationService.sendWebhook(WEBHOOK_URL, Map.of("event", "ping")));

        mockServer.verify();
    }
}
