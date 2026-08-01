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
    @WithMockUser
    void handlePaymentStatusWebhook_success_returnsAccepted() throws Exception {
        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-TEST123",
                "status", "succeeded"
        );

        PaymentResponse response = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-TEST123")
                .status(PaymentStatus.COMPLETED)
                .amount(new BigDecimal("100.00"))
                .build();

        when(paymentService.getPaymentByTransactionId("TXN-TEST123")).thenReturn(response);
        when(paymentService.updatePaymentStatus(eq(1L), any(PaymentStatus.class))).thenReturn(response);

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload))
                        .header("X-Webhook-Signature", "test-sig"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    @WithMockUser
    void handlePaymentStatusWebhook_noSignature_stillAccepted() throws Exception {
        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-TEST456",
                "status", "completed"
        );

        PaymentResponse response = PaymentResponse.builder()
                .id(2L)
                .transactionId("TXN-TEST456")
                .status(PaymentStatus.COMPLETED)
                .amount(new BigDecimal("50.00"))
                .build();

        when(paymentService.getPaymentByTransactionId("TXN-TEST456")).thenReturn(response);
        when(paymentService.updatePaymentStatus(eq(2L), any(PaymentStatus.class))).thenReturn(response);

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    @WithMockUser
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
    @WithMockUser
    void handlePaymentStatusWebhook_missingStatus_returnsBadRequest() throws Exception {
        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-TEST789"
        );

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing required fields: transaction_id, status"));
    }

    @Test
    @WithMockUser
    void handlePaymentStatusWebhook_failedStatus_mapsCorrectly() throws Exception {
        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-FAIL001",
                "status", "declined"
        );

        PaymentResponse response = PaymentResponse.builder()
                .id(3L)
                .transactionId("TXN-FAIL001")
                .status(PaymentStatus.FAILED)
                .amount(new BigDecimal("75.00"))
                .build();

        when(paymentService.getPaymentByTransactionId("TXN-FAIL001")).thenReturn(response);
        when(paymentService.updatePaymentStatus(eq(3L), eq(PaymentStatus.FAILED))).thenReturn(response);

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    @WithMockUser
    void handlePaymentStatusWebhook_processingError_returnsAcceptedWithWarning() throws Exception {
        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-ERR001",
                "status", "succeeded"
        );

        when(paymentService.getPaymentByTransactionId("TXN-ERR001"))
                .thenThrow(new RuntimeException("DB connection error"));

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.warning").value("processing_error"));
    }

    @Test
    @WithMockUser
    void handlePaymentStatusWebhook_refundedStatus_mapsCorrectly() throws Exception {
        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-REFUND001",
                "status", "refunded"
        );

        PaymentResponse response = PaymentResponse.builder()
                .id(4L)
                .transactionId("TXN-REFUND001")
                .status(PaymentStatus.REFUNDED)
                .amount(new BigDecimal("200.00"))
                .build();

        when(paymentService.getPaymentByTransactionId("TXN-REFUND001")).thenReturn(response);
        when(paymentService.updatePaymentStatus(eq(4L), eq(PaymentStatus.REFUNDED))).thenReturn(response);

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    @WithMockUser
    void handlePaymentStatusWebhook_unknownStatus_defaultsToProcessing() throws Exception {
        Map<String, Object> payload = Map.of(
                "transaction_id", "TXN-UNKNOWN001",
                "status", "some_unknown_status"
        );

        PaymentResponse response = PaymentResponse.builder()
                .id(5L)
                .transactionId("TXN-UNKNOWN001")
                .status(PaymentStatus.PROCESSING)
                .amount(new BigDecimal("150.00"))
                .build();

        when(paymentService.getPaymentByTransactionId("TXN-UNKNOWN001")).thenReturn(response);
        when(paymentService.updatePaymentStatus(eq(5L), eq(PaymentStatus.PROCESSING))).thenReturn(response);

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }
}
