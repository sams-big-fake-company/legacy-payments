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

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller tests for the unauthenticated gateway webhook receiver.
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

    private PaymentResponse existingPayment() {
        return PaymentResponse.builder()
                .id(42L)
                .transactionId("TXN-HOOK001")
                .status(PaymentStatus.PROCESSING)
                .build();
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
        when(paymentService.getPaymentByTransactionId("TXN-HOOK001")).thenReturn(existingPayment());

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", "sig-123")
                        .content(payload("TXN-HOOK001", gatewayStatus)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));

        verify(paymentService).updatePaymentStatus(42L, expected);
    }

    @Test
    void handlePaymentStatusWebhook_acceptsRequestWithoutSignature() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-HOOK001")).thenReturn(existingPayment());

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("TXN-HOOK001", "succeeded")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    void handlePaymentStatusWebhook_missingTransactionIdReturns400() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"succeeded\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing required fields: transaction_id, status"));

        verify(paymentService, never()).updatePaymentStatus(anyLong(), eq(PaymentStatus.COMPLETED));
    }

    @Test
    void handlePaymentStatusWebhook_missingStatusReturns400() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transaction_id\":\"TXN-HOOK001\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing required fields: transaction_id, status"));
    }

    @Test
    void handlePaymentStatusWebhook_unknownTransactionIsAcknowledgedWithWarning() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-MISSING"))
                .thenThrow(new PaymentException("Payment not found: TXN-MISSING", "PAYMENT_NOT_FOUND"));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("TXN-MISSING", "succeeded")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.warning").value("processing_error"));
    }
}
