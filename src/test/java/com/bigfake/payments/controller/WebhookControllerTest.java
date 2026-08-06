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

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller tests for WebhookController.
 *
 * The webhook endpoint is intentionally unauthenticated (see SecurityConfig) and
 * always answers 2xx so the gateway does not retry (PAY-4204).
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

    private String payload(String transactionId, String status) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("transaction_id", transactionId);
        body.put("status", status);
        return objectMapper.writeValueAsString(body);
    }

    private void stubPaymentLookup(String transactionId) {
        when(paymentService.getPaymentByTransactionId(transactionId))
                .thenReturn(PaymentResponse.builder().id(5L).transactionId(transactionId).build());
    }

    @ParameterizedTest
    @CsvSource({
            "succeeded,       COMPLETED",
            "success,         COMPLETED",
            "completed,       COMPLETED",
            "CAPTURED,        COMPLETED",
            "failed,          FAILED",
            "declined,        FAILED",
            "rejected,        FAILED",
            "error,           FAILED",
            "pending,         PROCESSING",
            "requires_action, PROCESSING",
            "processing,      PROCESSING",
            "refunded,        REFUNDED",
            "reversed,        REFUNDED",
            "something_new,   PROCESSING"
    })
    void handlePaymentStatusWebhook_mapsGatewayStatusToInternalStatus(String gatewayStatus,
                                                                      PaymentStatus expected) throws Exception {
        stubPaymentLookup("TXN-HOOK001");

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("TXN-HOOK001", gatewayStatus)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));

        verify(paymentService).updatePaymentStatus(5L, expected);
    }

    @Test
    void handlePaymentStatusWebhook_acceptsRequestWithSignatureHeader() throws Exception {
        stubPaymentLookup("TXN-HOOK002");

        mockMvc.perform(post(URL)
                        .header("X-Webhook-Signature", "sha256=deadbeef")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("TXN-HOOK002", "succeeded")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));

        verify(paymentService).updatePaymentStatus(5L, PaymentStatus.COMPLETED);
    }

    @Test
    void handlePaymentStatusWebhook_missingTransactionId_returns400() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"succeeded\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing required fields: transaction_id, status"));

        verify(paymentService, never()).getPaymentByTransactionId(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void handlePaymentStatusWebhook_missingStatus_returns400() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transaction_id\":\"TXN-HOOK003\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing required fields: transaction_id, status"));

        verify(paymentService, never()).getPaymentByTransactionId(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void handlePaymentStatusWebhook_unknownTransaction_stillReturns200WithWarning() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-MISSING"))
                .thenThrow(new PaymentException("Payment not found: TXN-MISSING", "PAYMENT_NOT_FOUND"));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("TXN-MISSING", "succeeded")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.warning").value("processing_error"));

        verify(paymentService, never()).updatePaymentStatus(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
    }
}
