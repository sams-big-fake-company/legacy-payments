package com.bigfake.payments.controller;

import com.bigfake.payments.exception.PaymentException;
import com.bigfake.payments.model.dto.PaymentResponse;
import com.bigfake.payments.model.enums.PaymentStatus;
import com.bigfake.payments.model.enums.PaymentType;
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
import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
    void handlePaymentStatusWebhook_succeeded_returnsAccepted() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-ABC123")
                .status(PaymentStatus.COMPLETED)
                .build();
        when(paymentService.getPaymentByTransactionId("TXN-ABC123")).thenReturn(response);
        when(paymentService.updatePaymentStatus(anyLong(), any(PaymentStatus.class))).thenReturn(response);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-ABC123",
                "status", "succeeded"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload))
                        .header("X-Webhook-Signature", "sig123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    @WithMockUser
    void handlePaymentStatusWebhook_failed_mapsCorrectly() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-ABC123")
                .status(PaymentStatus.FAILED)
                .build();
        when(paymentService.getPaymentByTransactionId("TXN-ABC123")).thenReturn(response);
        when(paymentService.updatePaymentStatus(anyLong(), any(PaymentStatus.class))).thenReturn(response);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-ABC123",
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
    void handlePaymentStatusWebhook_pending_mapsToProcessing() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-ABC123")
                .status(PaymentStatus.PROCESSING)
                .build();
        when(paymentService.getPaymentByTransactionId("TXN-ABC123")).thenReturn(response);
        when(paymentService.updatePaymentStatus(anyLong(), any(PaymentStatus.class))).thenReturn(response);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-ABC123",
                "status", "pending"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void handlePaymentStatusWebhook_refunded_mapsCorrectly() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-ABC123")
                .status(PaymentStatus.REFUNDED)
                .build();
        when(paymentService.getPaymentByTransactionId("TXN-ABC123")).thenReturn(response);
        when(paymentService.updatePaymentStatus(anyLong(), any(PaymentStatus.class))).thenReturn(response);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-ABC123",
                "status", "refunded"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void handlePaymentStatusWebhook_unknownStatus_defaultsToProcessing() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-ABC123")
                .status(PaymentStatus.PROCESSING)
                .build();
        when(paymentService.getPaymentByTransactionId("TXN-ABC123")).thenReturn(response);
        when(paymentService.updatePaymentStatus(anyLong(), any(PaymentStatus.class))).thenReturn(response);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-ABC123",
                "status", "unknown_status"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void handlePaymentStatusWebhook_missingTransactionId_returnsBadRequest() throws Exception {
        Map<String, Object> payload = Map.of("status", "succeeded");

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @WithMockUser
    void handlePaymentStatusWebhook_missingStatus_returnsBadRequest() throws Exception {
        Map<String, Object> payload = Map.of("transaction_id", "TXN-ABC123");

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void handlePaymentStatusWebhook_serviceError_returnsOkWithWarning() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-NOTFOUND"))
                .thenThrow(new PaymentException("Payment not found", "PAYMENT_NOT_FOUND"));

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-NOTFOUND",
                "status", "succeeded"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warning").value("processing_error"));
    }

    @Test
    @WithMockUser
    void handlePaymentStatusWebhook_noSignature_stillAccepted() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-ABC123")
                .status(PaymentStatus.COMPLETED)
                .build();
        when(paymentService.getPaymentByTransactionId("TXN-ABC123")).thenReturn(response);
        when(paymentService.updatePaymentStatus(anyLong(), any(PaymentStatus.class))).thenReturn(response);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-ABC123",
                "status", "completed"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }
}
