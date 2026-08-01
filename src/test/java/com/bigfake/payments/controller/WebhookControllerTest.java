package com.bigfake.payments.controller;

import com.bigfake.payments.model.dto.PaymentResponse;
import com.bigfake.payments.model.enums.PaymentStatus;
import com.bigfake.payments.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller tests for WebhookController.
 * Webhook endpoints are unauthenticated per SecurityConfig.
 */
@WebMvcTest(WebhookController.class)
class WebhookControllerTest {

    private static final String WEBHOOK_URL = "/api/webhooks/gateway/payment-status";

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

    private PaymentResponse paymentWithId(Long id) {
        return PaymentResponse.builder()
                .id(id)
                .transactionId("TXN-HOOK")
                .status(PaymentStatus.PROCESSING)
                .build();
    }

    @Test
    void webhook_successStatus_updatesPaymentToCompleted() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-HOOK")).thenReturn(paymentWithId(9L));

        mockMvc.perform(post(WEBHOOK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", "sig-123")
                        .content(objectMapper.writeValueAsString(payload("TXN-HOOK", "succeeded"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));

        verify(paymentService).updatePaymentStatus(9L, PaymentStatus.COMPLETED);
    }

    @Test
    void webhook_declinedStatus_updatesPaymentToFailed() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-HOOK")).thenReturn(paymentWithId(9L));

        mockMvc.perform(post(WEBHOOK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload("TXN-HOOK", "declined"))))
                .andExpect(status().isOk());

        verify(paymentService).updatePaymentStatus(9L, PaymentStatus.FAILED);
    }

    @Test
    void webhook_pendingStatus_updatesPaymentToProcessing() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-HOOK")).thenReturn(paymentWithId(9L));

        mockMvc.perform(post(WEBHOOK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload("TXN-HOOK", "requires_action"))))
                .andExpect(status().isOk());

        verify(paymentService).updatePaymentStatus(9L, PaymentStatus.PROCESSING);
    }

    @Test
    void webhook_reversedStatus_updatesPaymentToRefunded() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-HOOK")).thenReturn(paymentWithId(9L));

        mockMvc.perform(post(WEBHOOK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload("TXN-HOOK", "reversed"))))
                .andExpect(status().isOk());

        verify(paymentService).updatePaymentStatus(9L, PaymentStatus.REFUNDED);
    }

    @Test
    void webhook_unknownStatus_defaultsToProcessing() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-HOOK")).thenReturn(paymentWithId(9L));

        mockMvc.perform(post(WEBHOOK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload("TXN-HOOK", "weird_status"))))
                .andExpect(status().isOk());

        verify(paymentService).updatePaymentStatus(9L, PaymentStatus.PROCESSING);
    }

    @Test
    void webhook_missingTransactionId_returns400() throws Exception {
        mockMvc.perform(post(WEBHOOK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload(null, "succeeded"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        verify(paymentService, never()).getPaymentByTransactionId(eq("TXN-HOOK"));
    }

    @Test
    void webhook_missingStatus_returns400() throws Exception {
        mockMvc.perform(post(WEBHOOK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload("TXN-HOOK", null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void webhook_processingError_returns200WithWarning() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-HOOK"))
                .thenThrow(new RuntimeException("payment not found"));

        mockMvc.perform(post(WEBHOOK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload("TXN-HOOK", "succeeded"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.warning").value("processing_error"));
    }
}
