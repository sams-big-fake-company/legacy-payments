package com.bigfake.payments.controller;

import com.bigfake.payments.exception.PaymentException;
import com.bigfake.payments.model.dto.PaymentResponse;
import com.bigfake.payments.model.enums.PaymentStatus;
import com.bigfake.payments.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller tests for WebhookController.
 *
 * Webhook endpoints are intentionally unauthenticated, so no @WithMockUser here.
 */
@WebMvcTest(WebhookController.class)
class WebhookControllerTest {

    private static final String URL = "/api/webhooks/gateway/payment-status";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

    private static PaymentResponse payment() {
        return PaymentResponse.builder()
                .id(42L)
                .transactionId("TXN-ABC123")
                .status(PaymentStatus.PROCESSING)
                .build();
    }

    private org.springframework.test.web.servlet.ResultActions postPayload(Map<String, Object> payload) throws Exception {
        return mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)));
    }

    @ParameterizedTest(name = "gateway status {0} -> {1}")
    @CsvSource({
            "succeeded, COMPLETED",
            "success, COMPLETED",
            "completed, COMPLETED",
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
    void mapsGatewayStatusesToInternalStatuses(String gatewayStatus, PaymentStatus expected) throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-ABC123")).thenReturn(payment());

        postPayload(Map.of("transaction_id", "TXN-ABC123", "status", gatewayStatus))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));

        verify(paymentService).updatePaymentStatus(42L, expected);
    }

    @Test
    void acceptsWebhooksThatCarryASignatureHeader() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-ABC123")).thenReturn(payment());

        mockMvc.perform(post(URL)
                        .header("X-Webhook-Signature", "sha256=deadbeef")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("transaction_id", "TXN-ABC123", "status", "succeeded"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    void rejectsPayloadsWithoutATransactionId() throws Exception {
        postPayload(Map.of("status", "succeeded"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing required fields: transaction_id, status"));

        verify(paymentService, never()).getPaymentByTransactionId(any());
    }

    @Test
    void rejectsPayloadsWithoutAStatus() throws Exception {
        postPayload(Map.of("transaction_id", "TXN-ABC123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing required fields: transaction_id, status"));

        verify(paymentService, never()).updatePaymentStatus(any(), any());
    }

    @Test
    void acknowledgesWebhooksForUnknownTransactionsToStopGatewayRetries() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-UNKNOWN"))
                .thenThrow(new PaymentException("Payment not found: TXN-UNKNOWN", "PAYMENT_NOT_FOUND"));

        postPayload(Map.of("transaction_id", "TXN-UNKNOWN", "status", "succeeded"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.warning").value("processing_error"));
    }

    @Test
    void acknowledgesWebhooksWhenTheStatusUpdateIsRejected() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-ABC123")).thenReturn(payment());
        when(paymentService.updatePaymentStatus(42L, PaymentStatus.COMPLETED))
                .thenThrow(new PaymentException("Cannot transition from FAILED to COMPLETED", "INVALID_STATE_TRANSITION"));

        postPayload(Map.of("transaction_id", "TXN-ABC123", "status", "succeeded"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warning").value("processing_error"));
    }
}
