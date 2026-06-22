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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
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
    void handlePaymentStatusWebhook_successCompleted() throws Exception {
        PaymentResponse paymentResponse = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-123")
                .amount(new BigDecimal("100.00"))
                .status(PaymentStatus.COMPLETED)
                .build();

        when(paymentService.getPaymentByTransactionId("TXN-123")).thenReturn(paymentResponse);
        when(paymentService.updatePaymentStatus(eq(1L), eq(PaymentStatus.COMPLETED))).thenReturn(paymentResponse);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-123",
                "status", "succeeded"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload))
                        .header("X-Webhook-Signature", "sig-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    @WithMockUser
    void handlePaymentStatusWebhook_failedStatus() throws Exception {
        PaymentResponse paymentResponse = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-456")
                .status(PaymentStatus.FAILED)
                .build();

        when(paymentService.getPaymentByTransactionId("TXN-456")).thenReturn(paymentResponse);
        when(paymentService.updatePaymentStatus(eq(1L), eq(PaymentStatus.FAILED))).thenReturn(paymentResponse);

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
        PaymentResponse paymentResponse = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-789")
                .status(PaymentStatus.PROCESSING)
                .build();

        when(paymentService.getPaymentByTransactionId("TXN-789")).thenReturn(paymentResponse);
        when(paymentService.updatePaymentStatus(eq(1L), eq(PaymentStatus.PROCESSING))).thenReturn(paymentResponse);

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
        PaymentResponse paymentResponse = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-REF")
                .status(PaymentStatus.REFUNDED)
                .build();

        when(paymentService.getPaymentByTransactionId("TXN-REF")).thenReturn(paymentResponse);
        when(paymentService.updatePaymentStatus(eq(1L), eq(PaymentStatus.REFUNDED))).thenReturn(paymentResponse);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-REF",
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
    void handlePaymentStatusWebhook_unknownStatus_defaultsToProcessing() throws Exception {
        PaymentResponse paymentResponse = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-UNK")
                .status(PaymentStatus.PROCESSING)
                .build();

        when(paymentService.getPaymentByTransactionId("TXN-UNK")).thenReturn(paymentResponse);
        when(paymentService.updatePaymentStatus(eq(1L), eq(PaymentStatus.PROCESSING))).thenReturn(paymentResponse);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-UNK",
                "status", "unknown_status_xyz"
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
    void handlePaymentStatusWebhook_missingTransactionId() throws Exception {
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
    void handlePaymentStatusWebhook_missingStatus() throws Exception {
        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-123"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void handlePaymentStatusWebhook_processingError_returnsOk() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-ERR"))
                .thenThrow(new PaymentException("Not found", "PAYMENT_NOT_FOUND"));

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-ERR",
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
    void handlePaymentStatusWebhook_withoutSignature() throws Exception {
        PaymentResponse paymentResponse = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-NOSIG")
                .status(PaymentStatus.COMPLETED)
                .build();

        when(paymentService.getPaymentByTransactionId("TXN-NOSIG")).thenReturn(paymentResponse);
        when(paymentService.updatePaymentStatus(eq(1L), any())).thenReturn(paymentResponse);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-NOSIG",
                "status", "success"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }
}
