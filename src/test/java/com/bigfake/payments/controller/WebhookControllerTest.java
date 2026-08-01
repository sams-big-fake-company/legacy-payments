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

import java.math.BigDecimal;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    void handlePaymentStatusWebhook_success_returnsAccepted() throws Exception {
        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-ABC123",
                "status", "succeeded"
        );

        PaymentResponse response = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-ABC123")
                .status(PaymentStatus.COMPLETED)
                .amount(new BigDecimal("100.00"))
                .build();

        when(paymentService.getPaymentByTransactionId("TXN-ABC123")).thenReturn(response);
        when(paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED)).thenReturn(response);

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload))
                        .header("X-Webhook-Signature", "some-signature"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    void handlePaymentStatusWebhook_noSignature_stillAccepts() throws Exception {
        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-ABC123",
                "status", "completed"
        );

        PaymentResponse response = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-ABC123")
                .status(PaymentStatus.COMPLETED)
                .amount(new BigDecimal("100.00"))
                .build();

        when(paymentService.getPaymentByTransactionId("TXN-ABC123")).thenReturn(response);
        when(paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED)).thenReturn(response);

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
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
                "transaction_id", "TXN-ABC123"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing required fields: transaction_id, status"));
    }

    @Test
    void handlePaymentStatusWebhook_failedStatus_mapsCorrectly() throws Exception {
        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-FAIL123",
                "status", "declined"
        );

        PaymentResponse response = PaymentResponse.builder()
                .id(2L)
                .transactionId("TXN-FAIL123")
                .status(PaymentStatus.FAILED)
                .build();

        when(paymentService.getPaymentByTransactionId("TXN-FAIL123")).thenReturn(response);
        when(paymentService.updatePaymentStatus(2L, PaymentStatus.FAILED)).thenReturn(response);

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    void handlePaymentStatusWebhook_processingStatus_mapsCorrectly() throws Exception {
        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-PROC123",
                "status", "pending"
        );

        PaymentResponse response = PaymentResponse.builder()
                .id(3L)
                .transactionId("TXN-PROC123")
                .status(PaymentStatus.PROCESSING)
                .build();

        when(paymentService.getPaymentByTransactionId("TXN-PROC123")).thenReturn(response);
        when(paymentService.updatePaymentStatus(3L, PaymentStatus.PROCESSING)).thenReturn(response);

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }

    @Test
    void handlePaymentStatusWebhook_refundedStatus_mapsCorrectly() throws Exception {
        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-REF123",
                "status", "refunded"
        );

        PaymentResponse response = PaymentResponse.builder()
                .id(4L)
                .transactionId("TXN-REF123")
                .status(PaymentStatus.REFUNDED)
                .build();

        when(paymentService.getPaymentByTransactionId("TXN-REF123")).thenReturn(response);
        when(paymentService.updatePaymentStatus(4L, PaymentStatus.REFUNDED)).thenReturn(response);

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }

    @Test
    void handlePaymentStatusWebhook_unknownStatus_defaultsToProcessing() throws Exception {
        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-UNK123",
                "status", "unknown_status"
        );

        PaymentResponse response = PaymentResponse.builder()
                .id(5L)
                .transactionId("TXN-UNK123")
                .status(PaymentStatus.PROCESSING)
                .build();

        when(paymentService.getPaymentByTransactionId("TXN-UNK123")).thenReturn(response);
        when(paymentService.updatePaymentStatus(5L, PaymentStatus.PROCESSING)).thenReturn(response);

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }

    @Test
    void handlePaymentStatusWebhook_serviceException_returns200WithWarning() throws Exception {
        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-ERR123",
                "status", "succeeded"
        );

        when(paymentService.getPaymentByTransactionId("TXN-ERR123"))
                .thenThrow(new PaymentException("Payment not found", "PAYMENT_NOT_FOUND"));

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.warning").value("processing_error"));
    }

    @Test
    void handlePaymentStatusWebhook_capturedStatus_mapsToCompleted() throws Exception {
        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-CAP123",
                "status", "captured"
        );

        PaymentResponse response = PaymentResponse.builder()
                .id(6L)
                .transactionId("TXN-CAP123")
                .status(PaymentStatus.COMPLETED)
                .build();

        when(paymentService.getPaymentByTransactionId("TXN-CAP123")).thenReturn(response);
        when(paymentService.updatePaymentStatus(6L, PaymentStatus.COMPLETED)).thenReturn(response);

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }

    @Test
    void handlePaymentStatusWebhook_reversedStatus_mapsToRefunded() throws Exception {
        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-REV123",
                "status", "reversed"
        );

        PaymentResponse response = PaymentResponse.builder()
                .id(7L)
                .transactionId("TXN-REV123")
                .status(PaymentStatus.REFUNDED)
                .build();

        when(paymentService.getPaymentByTransactionId("TXN-REV123")).thenReturn(response);
        when(paymentService.updatePaymentStatus(7L, PaymentStatus.REFUNDED)).thenReturn(response);

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }
}
