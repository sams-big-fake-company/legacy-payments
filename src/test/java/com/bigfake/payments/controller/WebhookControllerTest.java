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
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller tests for WebhookController.
 *
 * Gateway webhooks are unauthenticated by design (see SecurityConfig).
 */
@WebMvcTest(WebhookController.class)
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

    private static PaymentResponse paymentWithId(Long id) {
        return PaymentResponse.builder()
                .id(id)
                .transactionId("TXN-ABC123")
                .status(PaymentStatus.PROCESSING)
                .build();
    }

    private ResultActions postWebhook(Map<String, Object> payload) throws Exception {
        return mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)));
    }

    @ParameterizedTest
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
    void handlePaymentStatusWebhook_mapsGatewayStatusToInternalStatus(String gatewayStatus,
                                                                      PaymentStatus expected) throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-ABC123")).thenReturn(paymentWithId(5L));

        postWebhook(Map.of("transaction_id", "TXN-ABC123", "status", gatewayStatus))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));

        verify(paymentService).updatePaymentStatus(5L, expected);
    }

    @Test
    void handlePaymentStatusWebhook_acceptsSignatureHeaderWhenPresent() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-ABC123")).thenReturn(paymentWithId(5L));

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .header("X-Webhook-Signature", "sha256=deadbeef")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("transaction_id", "TXN-ABC123", "status", "succeeded"))))
                .andExpect(status().isOk());

        verify(paymentService).updatePaymentStatus(5L, PaymentStatus.COMPLETED);
    }

    @Test
    void handlePaymentStatusWebhook_missingTransactionId_returns400() throws Exception {
        postWebhook(Map.of("status", "succeeded"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing required fields: transaction_id, status"));

        verify(paymentService, never()).updatePaymentStatus(anyLong(), any());
    }

    @Test
    void handlePaymentStatusWebhook_missingStatus_returns400() throws Exception {
        postWebhook(Map.of("transaction_id", "TXN-ABC123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing required fields: transaction_id, status"));
    }

    @Test
    void handlePaymentStatusWebhook_unknownTransaction_returns200WithWarning() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-MISSING"))
                .thenThrow(new PaymentException("Payment not found: TXN-MISSING", "PAYMENT_NOT_FOUND"));

        postWebhook(Map.of("transaction_id", "TXN-MISSING", "status", "succeeded"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.warning").value("processing_error"));
    }
}
