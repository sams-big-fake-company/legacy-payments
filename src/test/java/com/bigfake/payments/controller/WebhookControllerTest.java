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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
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
        PaymentResponse paymentResponse = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-WH001")
                .status(PaymentStatus.PROCESSING)
                .build();
        when(paymentService.getPaymentByTransactionId("TXN-WH001")).thenReturn(paymentResponse);
        when(paymentService.updatePaymentStatus(eq(1L), eq(PaymentStatus.COMPLETED))).thenReturn(paymentResponse);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-WH001",
                "status", "succeeded"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    void handlePaymentStatusWebhook_withSignature() throws Exception {
        PaymentResponse paymentResponse = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-WH002")
                .status(PaymentStatus.PROCESSING)
                .build();
        when(paymentService.getPaymentByTransactionId("TXN-WH002")).thenReturn(paymentResponse);
        when(paymentService.updatePaymentStatus(eq(1L), eq(PaymentStatus.COMPLETED))).thenReturn(paymentResponse);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-WH002",
                "status", "completed"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", "valid-sig")
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
                .andExpect(jsonPath("$.error").value("Missing required fields: transaction_id, status"));
    }

    @Test
    void handlePaymentStatusWebhook_missingStatus_returns400() throws Exception {
        Map<String, Object> payload = Map.of("transaction_id", "TXN-WH001");

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing required fields: transaction_id, status"));
    }

    @Test
    void handlePaymentStatusWebhook_failedStatus() throws Exception {
        PaymentResponse paymentResponse = PaymentResponse.builder()
                .id(2L)
                .transactionId("TXN-WH003")
                .status(PaymentStatus.PROCESSING)
                .build();
        when(paymentService.getPaymentByTransactionId("TXN-WH003")).thenReturn(paymentResponse);
        when(paymentService.updatePaymentStatus(eq(2L), eq(PaymentStatus.FAILED))).thenReturn(paymentResponse);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-WH003",
                "status", "declined"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    void handlePaymentStatusWebhook_refundedStatus() throws Exception {
        PaymentResponse paymentResponse = PaymentResponse.builder()
                .id(3L)
                .transactionId("TXN-WH004")
                .status(PaymentStatus.COMPLETED)
                .build();
        when(paymentService.getPaymentByTransactionId("TXN-WH004")).thenReturn(paymentResponse);
        when(paymentService.updatePaymentStatus(eq(3L), eq(PaymentStatus.REFUNDED))).thenReturn(paymentResponse);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-WH004",
                "status", "refunded"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    void handlePaymentStatusWebhook_unknownStatus_defaultsToProcessing() throws Exception {
        PaymentResponse paymentResponse = PaymentResponse.builder()
                .id(4L)
                .transactionId("TXN-WH005")
                .status(PaymentStatus.PENDING)
                .build();
        when(paymentService.getPaymentByTransactionId("TXN-WH005")).thenReturn(paymentResponse);
        when(paymentService.updatePaymentStatus(eq(4L), eq(PaymentStatus.PROCESSING))).thenReturn(paymentResponse);

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-WH005",
                "status", "some_unknown_status"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    void handlePaymentStatusWebhook_serviceException_returns200WithWarning() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-WH006"))
                .thenThrow(new PaymentException("Payment not found", "PAYMENT_NOT_FOUND"));

        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-WH006",
                "status", "succeeded"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.warning").value("processing_error"));
    }
}
