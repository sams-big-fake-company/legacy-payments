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

    private PaymentResponse buildPaymentResponse(Long id, PaymentStatus status) {
        return PaymentResponse.builder()
                .id(id)
                .transactionId("TXN-TEST123")
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(status)
                .paymentType(PaymentType.CREDIT_CARD)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @WithMockUser
    void handlePaymentStatusWebhook_succeeded_updatesStatus() throws Exception {
        PaymentResponse response = buildPaymentResponse(1L, PaymentStatus.COMPLETED);
        when(paymentService.getPaymentByTransactionId("TXN-TEST123")).thenReturn(response);
        when(paymentService.updatePaymentStatus(eq(1L), eq(PaymentStatus.COMPLETED))).thenReturn(response);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-TEST123",
                "status", "succeeded"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));

        verify(paymentService).updatePaymentStatus(1L, PaymentStatus.COMPLETED);
    }

    @Test
    @WithMockUser
    void handlePaymentStatusWebhook_failed_updatesStatus() throws Exception {
        PaymentResponse response = buildPaymentResponse(1L, PaymentStatus.FAILED);
        when(paymentService.getPaymentByTransactionId("TXN-TEST123")).thenReturn(response);
        when(paymentService.updatePaymentStatus(eq(1L), eq(PaymentStatus.FAILED))).thenReturn(response);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-TEST123",
                "status", "failed"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));

        verify(paymentService).updatePaymentStatus(1L, PaymentStatus.FAILED);
    }

    @Test
    @WithMockUser
    void handlePaymentStatusWebhook_declined_mapsToFailed() throws Exception {
        PaymentResponse response = buildPaymentResponse(1L, PaymentStatus.FAILED);
        when(paymentService.getPaymentByTransactionId("TXN-TEST123")).thenReturn(response);
        when(paymentService.updatePaymentStatus(eq(1L), eq(PaymentStatus.FAILED))).thenReturn(response);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-TEST123",
                "status", "declined"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        verify(paymentService).updatePaymentStatus(1L, PaymentStatus.FAILED);
    }

    @Test
    @WithMockUser
    void handlePaymentStatusWebhook_pending_mapsToProcessing() throws Exception {
        PaymentResponse response = buildPaymentResponse(1L, PaymentStatus.PROCESSING);
        when(paymentService.getPaymentByTransactionId("TXN-TEST123")).thenReturn(response);
        when(paymentService.updatePaymentStatus(eq(1L), eq(PaymentStatus.PROCESSING))).thenReturn(response);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-TEST123",
                "status", "pending"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        verify(paymentService).updatePaymentStatus(1L, PaymentStatus.PROCESSING);
    }

    @Test
    @WithMockUser
    void handlePaymentStatusWebhook_refunded_mapsToRefunded() throws Exception {
        PaymentResponse response = buildPaymentResponse(1L, PaymentStatus.REFUNDED);
        when(paymentService.getPaymentByTransactionId("TXN-TEST123")).thenReturn(response);
        when(paymentService.updatePaymentStatus(eq(1L), eq(PaymentStatus.REFUNDED))).thenReturn(response);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-TEST123",
                "status", "refunded"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        verify(paymentService).updatePaymentStatus(1L, PaymentStatus.REFUNDED);
    }

    @Test
    @WithMockUser
    void handlePaymentStatusWebhook_unknownStatus_mapsToProcessing() throws Exception {
        PaymentResponse response = buildPaymentResponse(1L, PaymentStatus.PROCESSING);
        when(paymentService.getPaymentByTransactionId("TXN-TEST123")).thenReturn(response);
        when(paymentService.updatePaymentStatus(eq(1L), eq(PaymentStatus.PROCESSING))).thenReturn(response);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-TEST123",
                "status", "some_unknown_status"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        verify(paymentService).updatePaymentStatus(1L, PaymentStatus.PROCESSING);
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
                "transaction_id", "TXN-TEST123"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void handlePaymentStatusWebhook_processingError_returns200WithWarning() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-TEST123"))
                .thenThrow(new PaymentException("Payment not found", "PAYMENT_NOT_FOUND"));

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-TEST123",
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
    void handlePaymentStatusWebhook_withSignatureHeader_accepts() throws Exception {
        PaymentResponse response = buildPaymentResponse(1L, PaymentStatus.COMPLETED);
        when(paymentService.getPaymentByTransactionId("TXN-TEST123")).thenReturn(response);
        when(paymentService.updatePaymentStatus(eq(1L), eq(PaymentStatus.COMPLETED))).thenReturn(response);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-TEST123",
                "status", "success"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .header("X-Webhook-Signature", "some-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void handlePaymentStatusWebhook_captured_mapsToCompleted() throws Exception {
        PaymentResponse response = buildPaymentResponse(1L, PaymentStatus.COMPLETED);
        when(paymentService.getPaymentByTransactionId("TXN-TEST123")).thenReturn(response);
        when(paymentService.updatePaymentStatus(eq(1L), eq(PaymentStatus.COMPLETED))).thenReturn(response);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-TEST123",
                "status", "captured"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        verify(paymentService).updatePaymentStatus(1L, PaymentStatus.COMPLETED);
    }
}
