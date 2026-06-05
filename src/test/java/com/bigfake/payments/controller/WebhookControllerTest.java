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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WebhookController.class)
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

    @Test
    @WithMockUser
    void handlePaymentStatusWebhook_successfulStatusUpdate() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-123")
                .amount(new BigDecimal("50.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        when(paymentService.getPaymentByTransactionId("TXN-123")).thenReturn(response);
        when(paymentService.updatePaymentStatus(eq(1L), eq(PaymentStatus.COMPLETED))).thenReturn(response);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-123",
                "status", "succeeded"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload))
                        .header("X-Webhook-Signature", "sig123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));

        verify(paymentService).getPaymentByTransactionId("TXN-123");
        verify(paymentService).updatePaymentStatus(1L, PaymentStatus.COMPLETED);
    }

    @Test
    @WithMockUser
    void handlePaymentStatusWebhook_failedStatus() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .id(2L)
                .transactionId("TXN-456")
                .status(PaymentStatus.FAILED)
                .build();
        when(paymentService.getPaymentByTransactionId("TXN-456")).thenReturn(response);
        when(paymentService.updatePaymentStatus(eq(2L), eq(PaymentStatus.FAILED))).thenReturn(response);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-456",
                "status", "declined"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    @WithMockUser
    void handlePaymentStatusWebhook_processingStatus() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .id(3L)
                .transactionId("TXN-789")
                .status(PaymentStatus.PROCESSING)
                .build();
        when(paymentService.getPaymentByTransactionId("TXN-789")).thenReturn(response);
        when(paymentService.updatePaymentStatus(eq(3L), eq(PaymentStatus.PROCESSING))).thenReturn(response);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-789",
                "status", "pending"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    @WithMockUser
    void handlePaymentStatusWebhook_refundedStatus() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .id(4L)
                .transactionId("TXN-REFUND")
                .status(PaymentStatus.REFUNDED)
                .build();
        when(paymentService.getPaymentByTransactionId("TXN-REFUND")).thenReturn(response);
        when(paymentService.updatePaymentStatus(eq(4L), eq(PaymentStatus.REFUNDED))).thenReturn(response);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-REFUND",
                "status", "refunded"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    @WithMockUser
    void handlePaymentStatusWebhook_unknownStatusDefaultsToProcessing() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .id(5L)
                .transactionId("TXN-UNKNOWN")
                .status(PaymentStatus.PROCESSING)
                .build();
        when(paymentService.getPaymentByTransactionId("TXN-UNKNOWN")).thenReturn(response);
        when(paymentService.updatePaymentStatus(eq(5L), eq(PaymentStatus.PROCESSING))).thenReturn(response);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-UNKNOWN",
                "status", "some_unknown_status"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    @WithMockUser
    void handlePaymentStatusWebhook_missingTransactionId_returns400() throws Exception {
        Map<String, Object> payload = Map.of(
                "status", "succeeded"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing required fields: transaction_id, status"));
    }

    @Test
    @WithMockUser
    void handlePaymentStatusWebhook_missingStatus_returns400() throws Exception {
        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-123"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing required fields: transaction_id, status"));
    }

    @Test
    @WithMockUser
    void handlePaymentStatusWebhook_serviceException_returns200WithWarning() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-ERR"))
                .thenThrow(new RuntimeException("Service error"));

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-ERR",
                "status", "succeeded"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.warning").value("processing_error"));
    }
}
