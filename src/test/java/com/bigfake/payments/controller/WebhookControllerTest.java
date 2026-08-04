package com.bigfake.payments.controller;

import com.bigfake.payments.exception.PaymentException;
import com.bigfake.payments.model.dto.PaymentResponse;
import com.bigfake.payments.model.enums.PaymentStatus;
import com.bigfake.payments.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the gateway webhook receiver, including the gateway status mapping.
 */
@ExtendWith(MockitoExtension.class)
class WebhookControllerTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private WebhookController webhookController;

    private static Map<String, Object> payload(String transactionId, String status) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction_id", transactionId);
        payload.put("status", status);
        return payload;
    }

    private void paymentExists(String transactionId, Long id) {
        when(paymentService.getPaymentByTransactionId(transactionId))
                .thenReturn(PaymentResponse.builder().id(id).transactionId(transactionId).build());
    }

    @ParameterizedTest
    @CsvSource({
            "succeeded, COMPLETED",
            "success, COMPLETED",
            "COMPLETED, COMPLETED",
            "captured, COMPLETED",
            "failed, FAILED",
            "declined, FAILED",
            "rejected, FAILED",
            "error, FAILED",
            "pending, PROCESSING",
            "requires_action, PROCESSING",
            "processing, PROCESSING",
            "refunded, REFUNDED",
            "reversed, REFUNDED",
            "something_new, PROCESSING"
    })
    void handlePaymentStatusWebhook_mapsGatewayStatusToInternalStatus(String gatewayStatus, PaymentStatus expected) {
        paymentExists("TXN-1", 42L);

        ResponseEntity<Map<String, String>> response =
                webhookController.handlePaymentStatusWebhook(payload("TXN-1", gatewayStatus), "sig-123");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Map.of("status", "accepted"), response.getBody());
        verify(paymentService).updatePaymentStatus(42L, expected);
    }

    @Test
    void handlePaymentStatusWebhook_acceptsRequestsWithoutASignature() {
        paymentExists("TXN-1", 42L);

        ResponseEntity<Map<String, String>> response =
                webhookController.handlePaymentStatusWebhook(payload("TXN-1", "succeeded"), null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(paymentService).updatePaymentStatus(42L, PaymentStatus.COMPLETED);
    }

    @Test
    void handlePaymentStatusWebhook_rejectsPayloadWithoutTransactionId() {
        ResponseEntity<Map<String, String>> response =
                webhookController.handlePaymentStatusWebhook(payload(null, "succeeded"), "sig-123");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Missing required fields: transaction_id, status", response.getBody().get("error"));
        verifyNoInteractions(paymentService);
    }

    @Test
    void handlePaymentStatusWebhook_rejectsPayloadWithoutStatus() {
        ResponseEntity<Map<String, String>> response =
                webhookController.handlePaymentStatusWebhook(payload("TXN-1", null), "sig-123");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(paymentService);
    }

    @Test
    void handlePaymentStatusWebhook_rejectsEmptyPayload() {
        ResponseEntity<Map<String, String>> response =
                webhookController.handlePaymentStatusWebhook(new HashMap<>(), null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void handlePaymentStatusWebhook_returnsAcceptedWithWarningWhenTheLookupFails() {
        when(paymentService.getPaymentByTransactionId("TXN-MISSING"))
                .thenThrow(new PaymentException("Payment not found: TXN-MISSING", "PAYMENT_NOT_FOUND"));

        ResponseEntity<Map<String, String>> response =
                webhookController.handlePaymentStatusWebhook(payload("TXN-MISSING", "succeeded"), "sig-123");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("processing_error", response.getBody().get("warning"));
    }

    @Test
    void handlePaymentStatusWebhook_returnsAcceptedWithWarningWhenTheUpdateFails() {
        paymentExists("TXN-1", 42L);
        when(paymentService.updatePaymentStatus(42L, PaymentStatus.COMPLETED))
                .thenThrow(new PaymentException("Cannot transition", "INVALID_STATE_TRANSITION"));

        ResponseEntity<Map<String, String>> response =
                webhookController.handlePaymentStatusWebhook(payload("TXN-1", "succeeded"), "sig-123");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("accepted", response.getBody().get("status"));
        assertEquals("processing_error", response.getBody().get("warning"));
    }
}
