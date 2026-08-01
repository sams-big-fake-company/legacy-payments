package com.bigfake.payments.controller;

import com.bigfake.payments.exception.PaymentException;
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

import java.util.Map;

import static org.mockito.Mockito.*;
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
    void handlePaymentStatusWebhook_success() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-123")
                .status(PaymentStatus.COMPLETED)
                .build();
        when(paymentService.getPaymentByTransactionId("TXN-123")).thenReturn(response);
        when(paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED)).thenReturn(response);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-123",
                "status", "succeeded"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));

        verify(paymentService).updatePaymentStatus(1L, PaymentStatus.COMPLETED);
    }

    @Test
    void handlePaymentStatusWebhook_withSignature() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-123")
                .status(PaymentStatus.COMPLETED)
                .build();
        when(paymentService.getPaymentByTransactionId("TXN-123")).thenReturn(response);
        when(paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED)).thenReturn(response);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-123",
                "status", "completed"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", "sig-123")
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    void handlePaymentStatusWebhook_missingTransactionId_returns400() throws Exception {
        Map<String, Object> payload = Map.of("status", "succeeded");

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void handlePaymentStatusWebhook_missingStatus_returns400() throws Exception {
        Map<String, Object> payload = Map.of("transaction_id", "TXN-123");

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void handlePaymentStatusWebhook_processingError_returns200WithWarning() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-123"))
                .thenThrow(new PaymentException("Not found", "PAYMENT_NOT_FOUND"));

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-123",
                "status", "succeeded"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.warning").value("processing_error"));
    }

    @Test
    void handlePaymentStatusWebhook_failedStatus_mapsFailed() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-123")
                .status(PaymentStatus.FAILED)
                .build();
        when(paymentService.getPaymentByTransactionId("TXN-123")).thenReturn(response);
        when(paymentService.updatePaymentStatus(1L, PaymentStatus.FAILED)).thenReturn(response);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-123",
                "status", "declined"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        verify(paymentService).updatePaymentStatus(1L, PaymentStatus.FAILED);
    }

    @Test
    void handlePaymentStatusWebhook_pendingStatus_mapsProcessing() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-123")
                .status(PaymentStatus.PROCESSING)
                .build();
        when(paymentService.getPaymentByTransactionId("TXN-123")).thenReturn(response);
        when(paymentService.updatePaymentStatus(1L, PaymentStatus.PROCESSING)).thenReturn(response);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-123",
                "status", "pending"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        verify(paymentService).updatePaymentStatus(1L, PaymentStatus.PROCESSING);
    }

    @Test
    void handlePaymentStatusWebhook_refundedStatus_mapsRefunded() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-123")
                .status(PaymentStatus.REFUNDED)
                .build();
        when(paymentService.getPaymentByTransactionId("TXN-123")).thenReturn(response);
        when(paymentService.updatePaymentStatus(1L, PaymentStatus.REFUNDED)).thenReturn(response);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-123",
                "status", "refunded"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        verify(paymentService).updatePaymentStatus(1L, PaymentStatus.REFUNDED);
    }

    @Test
    void handlePaymentStatusWebhook_unknownStatus_defaultsToProcessing() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-123")
                .status(PaymentStatus.PROCESSING)
                .build();
        when(paymentService.getPaymentByTransactionId("TXN-123")).thenReturn(response);
        when(paymentService.updatePaymentStatus(1L, PaymentStatus.PROCESSING)).thenReturn(response);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-123",
                "status", "some_unknown_status"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        verify(paymentService).updatePaymentStatus(1L, PaymentStatus.PROCESSING);
    }
}
