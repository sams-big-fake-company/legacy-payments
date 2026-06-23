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
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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

    private PaymentResponse mockPaymentResponse(PaymentStatus status) {
        return PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-WEBHOOK")
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(status)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();
    }

    @Test
    void handlePaymentStatusWebhook_succeededStatus() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction_id", "TXN-WEBHOOK");
        payload.put("status", "succeeded");

        when(paymentService.getPaymentByTransactionId("TXN-WEBHOOK"))
                .thenReturn(mockPaymentResponse(PaymentStatus.PROCESSING));
        when(paymentService.updatePaymentStatus(eq(1L), eq(PaymentStatus.COMPLETED)))
                .thenReturn(mockPaymentResponse(PaymentStatus.COMPLETED));

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    void handlePaymentStatusWebhook_failedStatus() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction_id", "TXN-WEBHOOK");
        payload.put("status", "failed");

        when(paymentService.getPaymentByTransactionId("TXN-WEBHOOK"))
                .thenReturn(mockPaymentResponse(PaymentStatus.PROCESSING));
        when(paymentService.updatePaymentStatus(eq(1L), eq(PaymentStatus.FAILED)))
                .thenReturn(mockPaymentResponse(PaymentStatus.FAILED));

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    void handlePaymentStatusWebhook_declinedStatus() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction_id", "TXN-WEBHOOK");
        payload.put("status", "declined");

        when(paymentService.getPaymentByTransactionId("TXN-WEBHOOK"))
                .thenReturn(mockPaymentResponse(PaymentStatus.PROCESSING));
        when(paymentService.updatePaymentStatus(eq(1L), eq(PaymentStatus.FAILED)))
                .thenReturn(mockPaymentResponse(PaymentStatus.FAILED));

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    void handlePaymentStatusWebhook_pendingStatus() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction_id", "TXN-WEBHOOK");
        payload.put("status", "pending");

        when(paymentService.getPaymentByTransactionId("TXN-WEBHOOK"))
                .thenReturn(mockPaymentResponse(PaymentStatus.PENDING));
        when(paymentService.updatePaymentStatus(eq(1L), eq(PaymentStatus.PROCESSING)))
                .thenReturn(mockPaymentResponse(PaymentStatus.PROCESSING));

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    void handlePaymentStatusWebhook_refundedStatus() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction_id", "TXN-WEBHOOK");
        payload.put("status", "refunded");

        when(paymentService.getPaymentByTransactionId("TXN-WEBHOOK"))
                .thenReturn(mockPaymentResponse(PaymentStatus.COMPLETED));
        when(paymentService.updatePaymentStatus(eq(1L), eq(PaymentStatus.REFUNDED)))
                .thenReturn(mockPaymentResponse(PaymentStatus.REFUNDED));

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    void handlePaymentStatusWebhook_unknownStatusDefaultsToProcessing() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction_id", "TXN-WEBHOOK");
        payload.put("status", "unknown_status_xyz");

        when(paymentService.getPaymentByTransactionId("TXN-WEBHOOK"))
                .thenReturn(mockPaymentResponse(PaymentStatus.PENDING));
        when(paymentService.updatePaymentStatus(eq(1L), eq(PaymentStatus.PROCESSING)))
                .thenReturn(mockPaymentResponse(PaymentStatus.PROCESSING));

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    void handlePaymentStatusWebhook_missingTransactionId() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", "succeeded");

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing required fields: transaction_id, status"));
    }

    @Test
    void handlePaymentStatusWebhook_missingStatus() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction_id", "TXN-WEBHOOK");

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void handlePaymentStatusWebhook_processingError_returnsOkWithWarning() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction_id", "TXN-WEBHOOK");
        payload.put("status", "succeeded");

        when(paymentService.getPaymentByTransactionId("TXN-WEBHOOK"))
                .thenThrow(new PaymentException("Payment not found", "PAYMENT_NOT_FOUND"));

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.warning").value("processing_error"));
    }

    @Test
    void handlePaymentStatusWebhook_withSignatureHeader() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction_id", "TXN-WEBHOOK");
        payload.put("status", "success");

        when(paymentService.getPaymentByTransactionId("TXN-WEBHOOK"))
                .thenReturn(mockPaymentResponse(PaymentStatus.PROCESSING));
        when(paymentService.updatePaymentStatus(eq(1L), eq(PaymentStatus.COMPLETED)))
                .thenReturn(mockPaymentResponse(PaymentStatus.COMPLETED));

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", "sig_test123")
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    void handlePaymentStatusWebhook_capturedStatus() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction_id", "TXN-WEBHOOK");
        payload.put("status", "captured");

        when(paymentService.getPaymentByTransactionId("TXN-WEBHOOK"))
                .thenReturn(mockPaymentResponse(PaymentStatus.PROCESSING));
        when(paymentService.updatePaymentStatus(eq(1L), eq(PaymentStatus.COMPLETED)))
                .thenReturn(mockPaymentResponse(PaymentStatus.COMPLETED));

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    void handlePaymentStatusWebhook_rejectedStatus() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction_id", "TXN-WEBHOOK");
        payload.put("status", "rejected");

        when(paymentService.getPaymentByTransactionId("TXN-WEBHOOK"))
                .thenReturn(mockPaymentResponse(PaymentStatus.PROCESSING));
        when(paymentService.updatePaymentStatus(eq(1L), eq(PaymentStatus.FAILED)))
                .thenReturn(mockPaymentResponse(PaymentStatus.FAILED));

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }

    @Test
    void handlePaymentStatusWebhook_errorStatus() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction_id", "TXN-WEBHOOK");
        payload.put("status", "error");

        when(paymentService.getPaymentByTransactionId("TXN-WEBHOOK"))
                .thenReturn(mockPaymentResponse(PaymentStatus.PROCESSING));
        when(paymentService.updatePaymentStatus(eq(1L), eq(PaymentStatus.FAILED)))
                .thenReturn(mockPaymentResponse(PaymentStatus.FAILED));

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }

    @Test
    void handlePaymentStatusWebhook_requiresActionStatus() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction_id", "TXN-WEBHOOK");
        payload.put("status", "requires_action");

        when(paymentService.getPaymentByTransactionId("TXN-WEBHOOK"))
                .thenReturn(mockPaymentResponse(PaymentStatus.PENDING));
        when(paymentService.updatePaymentStatus(eq(1L), eq(PaymentStatus.PROCESSING)))
                .thenReturn(mockPaymentResponse(PaymentStatus.PROCESSING));

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }

    @Test
    void handlePaymentStatusWebhook_processingStatus() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction_id", "TXN-WEBHOOK");
        payload.put("status", "processing");

        when(paymentService.getPaymentByTransactionId("TXN-WEBHOOK"))
                .thenReturn(mockPaymentResponse(PaymentStatus.PENDING));
        when(paymentService.updatePaymentStatus(eq(1L), eq(PaymentStatus.PROCESSING)))
                .thenReturn(mockPaymentResponse(PaymentStatus.PROCESSING));

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }

    @Test
    void handlePaymentStatusWebhook_reversedStatus() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction_id", "TXN-WEBHOOK");
        payload.put("status", "reversed");

        when(paymentService.getPaymentByTransactionId("TXN-WEBHOOK"))
                .thenReturn(mockPaymentResponse(PaymentStatus.COMPLETED));
        when(paymentService.updatePaymentStatus(eq(1L), eq(PaymentStatus.REFUNDED)))
                .thenReturn(mockPaymentResponse(PaymentStatus.REFUNDED));

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }

    @Test
    void handlePaymentStatusWebhook_completedStatus() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction_id", "TXN-WEBHOOK");
        payload.put("status", "completed");

        when(paymentService.getPaymentByTransactionId("TXN-WEBHOOK"))
                .thenReturn(mockPaymentResponse(PaymentStatus.PROCESSING));
        when(paymentService.updatePaymentStatus(eq(1L), eq(PaymentStatus.COMPLETED)))
                .thenReturn(mockPaymentResponse(PaymentStatus.COMPLETED));

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }
}
