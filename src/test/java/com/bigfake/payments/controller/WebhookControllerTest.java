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

import java.math.BigDecimal;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
    void handlePaymentStatusWebhook_succeeded_returns200() throws Exception {
        PaymentResponse paymentResponse = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-WH001")
                .status(PaymentStatus.COMPLETED)
                .amount(new BigDecimal("100.00"))
                .build();

        when(paymentService.getPaymentByTransactionId("TXN-WH001")).thenReturn(paymentResponse);
        when(paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED)).thenReturn(paymentResponse);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-WH001",
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
    void handlePaymentStatusWebhook_failed_mapsCorrectly() throws Exception {
        PaymentResponse paymentResponse = PaymentResponse.builder()
                .id(2L)
                .transactionId("TXN-WH002")
                .status(PaymentStatus.FAILED)
                .build();

        when(paymentService.getPaymentByTransactionId("TXN-WH002")).thenReturn(paymentResponse);
        when(paymentService.updatePaymentStatus(2L, PaymentStatus.FAILED)).thenReturn(paymentResponse);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-WH002",
                "status", "failed"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));

        verify(paymentService).updatePaymentStatus(2L, PaymentStatus.FAILED);
    }

    @Test
    void handlePaymentStatusWebhook_pending_mapsToProcessing() throws Exception {
        PaymentResponse paymentResponse = PaymentResponse.builder()
                .id(3L)
                .transactionId("TXN-WH003")
                .status(PaymentStatus.PROCESSING)
                .build();

        when(paymentService.getPaymentByTransactionId("TXN-WH003")).thenReturn(paymentResponse);
        when(paymentService.updatePaymentStatus(3L, PaymentStatus.PROCESSING)).thenReturn(paymentResponse);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-WH003",
                "status", "pending"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));

        verify(paymentService).updatePaymentStatus(3L, PaymentStatus.PROCESSING);
    }

    @Test
    void handlePaymentStatusWebhook_refunded_mapsCorrectly() throws Exception {
        PaymentResponse paymentResponse = PaymentResponse.builder()
                .id(4L)
                .transactionId("TXN-WH004")
                .status(PaymentStatus.REFUNDED)
                .build();

        when(paymentService.getPaymentByTransactionId("TXN-WH004")).thenReturn(paymentResponse);
        when(paymentService.updatePaymentStatus(4L, PaymentStatus.REFUNDED)).thenReturn(paymentResponse);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-WH004",
                "status", "refunded"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));

        verify(paymentService).updatePaymentStatus(4L, PaymentStatus.REFUNDED);
    }

    @Test
    void handlePaymentStatusWebhook_unknownStatus_defaultsToProcessing() throws Exception {
        PaymentResponse paymentResponse = PaymentResponse.builder()
                .id(5L)
                .transactionId("TXN-WH005")
                .status(PaymentStatus.PROCESSING)
                .build();

        when(paymentService.getPaymentByTransactionId("TXN-WH005")).thenReturn(paymentResponse);
        when(paymentService.updatePaymentStatus(5L, PaymentStatus.PROCESSING)).thenReturn(paymentResponse);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-WH005",
                "status", "some_unknown_status"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));

        verify(paymentService).updatePaymentStatus(5L, PaymentStatus.PROCESSING);
    }

    @Test
    void handlePaymentStatusWebhook_missingTransactionId_returnsBadRequest() throws Exception {
        Map<String, Object> payload = Map.of(
                "status", "succeeded"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing required fields: transaction_id, status"));
    }

    @Test
    void handlePaymentStatusWebhook_missingStatus_returnsBadRequest() throws Exception {
        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-WH006"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing required fields: transaction_id, status"));
    }

    @Test
    void handlePaymentStatusWebhook_processingError_returns200WithWarning() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-WH007"))
                .thenThrow(new RuntimeException("DB error"));

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-WH007",
                "status", "succeeded"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warning").value("processing_error"));
    }

    @Test
    void handlePaymentStatusWebhook_withSignatureHeader_accepted() throws Exception {
        PaymentResponse paymentResponse = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-WH008")
                .status(PaymentStatus.COMPLETED)
                .build();

        when(paymentService.getPaymentByTransactionId("TXN-WH008")).thenReturn(paymentResponse);
        when(paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED)).thenReturn(paymentResponse);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-WH008",
                "status", "completed"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", "some-signature")
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    void handlePaymentStatusWebhook_gatewayStatusVariants() throws Exception {
        // Test "captured" -> COMPLETED
        PaymentResponse paymentResponse = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-CAPTURED")
                .status(PaymentStatus.COMPLETED)
                .build();

        when(paymentService.getPaymentByTransactionId("TXN-CAPTURED")).thenReturn(paymentResponse);
        when(paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED)).thenReturn(paymentResponse);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-CAPTURED",
                "status", "captured"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
        verify(paymentService).updatePaymentStatus(1L, PaymentStatus.COMPLETED);
    }
}
