package com.bigfake.payments.controller;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for WebhookController.
 */
@WebMvcTest(WebhookController.class)
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

    private Map<String, Object> validPayload(String status) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction_id", "TXN-WEBHOOK1");
        payload.put("status", status);
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
    void webhook_validSucceededStatus_updatesPaymentToCompleted() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-WEBHOOK1")).thenReturn(paymentResponse());

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validPayload("succeeded"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));

        verify(paymentService).updatePaymentStatus(1L, PaymentStatus.COMPLETED);
    }

    @ParameterizedTest
    @CsvSource({
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
    void webhook_mapsGatewayStatusToInternalStatus(String gatewayStatus, PaymentStatus expected) throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-WEBHOOK1")).thenReturn(paymentResponse());

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validPayload(gatewayStatus))))
                .andExpect(status().isOk());

        verify(paymentService).updatePaymentStatus(1L, expected);
    }

    @Test
    void webhook_missingTransactionId_returns400() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", "succeeded");

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        verify(paymentService, never()).updatePaymentStatus(any(), any());
    }

    @Test
    void webhook_missingStatus_returns400() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction_id", "TXN-WEBHOOK1");

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void webhook_processingError_returns200WithWarning() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-WEBHOOK1"))
                .thenThrow(new RuntimeException("db down"));

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validPayload("succeeded"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.warning").value("processing_error"));
    }

    @Test
    void webhook_withSignatureHeader_isAccepted() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-WEBHOOK1")).thenReturn(paymentResponse());

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .header("X-Webhook-Signature", "sig-abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validPayload("succeeded"))))
                .andExpect(status().isOk());

        verify(paymentService).updatePaymentStatus(eq(1L), eq(PaymentStatus.COMPLETED));
    }
}
