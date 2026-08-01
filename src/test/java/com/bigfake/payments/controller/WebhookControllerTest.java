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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WebhookController.class)
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

    private Map<String, Object> payload(String transactionId, String status) {
        Map<String, Object> payload = new HashMap<>();
        if (transactionId != null) {
            payload.put("transaction_id", transactionId);
        }
        if (status != null) {
            payload.put("status", status);
        }
        return payload;
    }

    private PaymentResponse paymentResponse() {
        return PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-WEBHOOK1")
                .status(PaymentStatus.PROCESSING)
                .build();
    }

    @Test
    void handleWebhook_validPayloadReturnsAccepted() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-WEBHOOK1")).thenReturn(paymentResponse());

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload("TXN-WEBHOOK1", "succeeded"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));

        verify(paymentService).updatePaymentStatus(1L, PaymentStatus.COMPLETED);
    }

    @ParameterizedTest
    @CsvSource({
            "succeeded, COMPLETED",
            "success, COMPLETED",
            "completed, COMPLETED",
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
            "something_unknown, PROCESSING"
    })
    void handleWebhook_mapsGatewayStatuses(String gatewayStatus, PaymentStatus expected) throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-WEBHOOK1")).thenReturn(paymentResponse());

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload("TXN-WEBHOOK1", gatewayStatus))))
                .andExpect(status().isOk());

        verify(paymentService).updatePaymentStatus(1L, expected);
    }

    @Test
    void handleWebhook_missingTransactionIdReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload(null, "succeeded"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing required fields: transaction_id, status"));

        verify(paymentService, never()).updatePaymentStatus(any(), any());
    }

    @Test
    void handleWebhook_missingStatusReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload("TXN-WEBHOOK1", null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void handleWebhook_processingErrorStillReturnsOkWithWarning() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-UNKNOWN"))
                .thenThrow(new PaymentException("Payment not found", "PAYMENT_NOT_FOUND"));

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload("TXN-UNKNOWN", "succeeded"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.warning").value("processing_error"));
    }

    @Test
    void handleWebhook_acceptsSignatureHeader() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-WEBHOOK1")).thenReturn(paymentResponse());

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .header("X-Webhook-Signature", "sig-abc123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload("TXN-WEBHOOK1", "succeeded"))))
                .andExpect(status().isOk());
    }
}
