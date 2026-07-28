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

import java.util.Collections;
import java.util.HashMap;
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
 * Web layer tests for {@link WebhookController}, including the gateway status
 * mapping and the "always answer 200" contract towards the gateway.
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

    private void givenPaymentExists(String transactionId) {
        when(paymentService.getPaymentByTransactionId(transactionId))
                .thenReturn(PaymentResponse.builder().id(10L).transactionId(transactionId).build());
    }

    @ParameterizedTest(name = "gateway status \"{0}\" maps to {1}")
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
    void mapsGatewayStatusesToInternalStatuses(String gatewayStatus, PaymentStatus expected) throws Exception {
        givenPaymentExists("TXN-1");

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("TXN-1", gatewayStatus)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));

        verify(paymentService).updatePaymentStatus(10L, expected);
    }

    @Test
    void acceptsWebhooksWithoutASignatureHeader() throws Exception {
        givenPaymentExists("TXN-1");

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("TXN-1", "succeeded")))
                .andExpect(status().isOk());
    }

    @Test
    void acceptsWebhooksWithASignatureHeader() throws Exception {
        givenPaymentExists("TXN-1");

        mockMvc.perform(post(URL)
                        .header("X-Webhook-Signature", "sha256=deadbeef")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("TXN-1", "succeeded")))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsPayloadsMissingTheTransactionId() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Collections.singletonMap("status", "succeeded"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("Missing required fields: transaction_id, status"));

        verify(paymentService, never()).updatePaymentStatus(anyLong(), any());
    }

    @Test
    void rejectsPayloadsMissingTheStatus() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Collections.singletonMap("transaction_id", "TXN-1"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void answers200WithAWarningWhenThePaymentCannotBeUpdated() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-UNKNOWN"))
                .thenThrow(new PaymentException("Payment not found: TXN-UNKNOWN", "PAYMENT_NOT_FOUND"));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("TXN-UNKNOWN", "succeeded")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.warning").value("processing_error"));
    }

    @Test
    void webhookEndpointDoesNotRequireAuthentication() throws Exception {
        givenPaymentExists("TXN-1");

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("TXN-1", "succeeded")))
                .andExpect(status().isOk());
    }
}
