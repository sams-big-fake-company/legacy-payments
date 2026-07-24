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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for WebhookController.
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

    @ParameterizedTest
    @CsvSource({
            "succeeded, COMPLETED",
            "success, COMPLETED",
            "completed, COMPLETED",
            "captured, COMPLETED",
            "CAPTURED, COMPLETED",
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
    void gatewayStatusIsMappedToInternalStatus(String gatewayStatus, PaymentStatus expected) {
        when(paymentService.getPaymentByTransactionId("TXN-1"))
                .thenReturn(PaymentResponse.builder().id(9L).transactionId("TXN-1").build());

        ResponseEntity<Map<String, String>> response =
                webhookController.handlePaymentStatusWebhook(payload("TXN-1", gatewayStatus), "sig");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Map.of("status", "accepted"), response.getBody());
        verify(paymentService).updatePaymentStatus(9L, expected);
    }

    @Test
    void missingSignatureIsAcceptedAnyway() {
        when(paymentService.getPaymentByTransactionId("TXN-1"))
                .thenReturn(PaymentResponse.builder().id(9L).transactionId("TXN-1").build());

        ResponseEntity<Map<String, String>> response =
                webhookController.handlePaymentStatusWebhook(payload("TXN-1", "succeeded"), null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(paymentService).updatePaymentStatus(9L, PaymentStatus.COMPLETED);
    }

    @Test
    void missingTransactionIdIsRejected() {
        ResponseEntity<Map<String, String>> response =
                webhookController.handlePaymentStatusWebhook(payload(null, "succeeded"), "sig");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Missing required fields: transaction_id, status", response.getBody().get("error"));
        verifyNoInteractions(paymentService);
    }

    @Test
    void missingStatusIsRejected() {
        ResponseEntity<Map<String, String>> response =
                webhookController.handlePaymentStatusWebhook(payload("TXN-1", null), "sig");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(paymentService);
    }

    @Test
    void unknownTransactionReturnsOkWithProcessingWarning() {
        when(paymentService.getPaymentByTransactionId("TXN-MISSING"))
                .thenThrow(new PaymentException("Payment not found: TXN-MISSING", "PAYMENT_NOT_FOUND"));

        ResponseEntity<Map<String, String>> response =
                webhookController.handlePaymentStatusWebhook(payload("TXN-MISSING", "succeeded"), "sig");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("processing_error", response.getBody().get("warning"));
        verify(paymentService, never()).updatePaymentStatus(anyLong(), any());
    }

    @Test
    void statusUpdateFailureIsSwallowedWithWarning() {
        when(paymentService.getPaymentByTransactionId("TXN-1"))
                .thenReturn(PaymentResponse.builder().id(9L).transactionId("TXN-1").build());
        when(paymentService.updatePaymentStatus(9L, PaymentStatus.COMPLETED))
                .thenThrow(new PaymentException("Cannot transition", "INVALID_STATE_TRANSITION"));

        ResponseEntity<Map<String, String>> response =
                webhookController.handlePaymentStatusWebhook(payload("TXN-1", "succeeded"), "sig");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("processing_error", response.getBody().get("warning"));
    }
}
