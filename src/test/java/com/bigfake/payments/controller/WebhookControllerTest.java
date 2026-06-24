package com.bigfake.payments.controller;

import com.bigfake.payments.exception.PaymentException;
import com.bigfake.payments.model.dto.PaymentResponse;
import com.bigfake.payments.model.enums.PaymentStatus;
import com.bigfake.payments.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.HashMap;
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
    void handlePaymentStatusWebhook_success() throws Exception {
        PaymentResponse paymentResponse = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-WH001")
                .status(PaymentStatus.COMPLETED)
                .amount(new BigDecimal("100.00"))
                .build();
        when(paymentService.getPaymentByTransactionId("TXN-WH001")).thenReturn(paymentResponse);
        when(paymentService.updatePaymentStatus(eq(1L), any(PaymentStatus.class))).thenReturn(paymentResponse);

        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction_id", "TXN-WH001");
        payload.put("status", "succeeded");

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
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", "succeeded");

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @WithMockUser
    void handlePaymentStatusWebhook_missingStatus() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction_id", "TXN-WH001");

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void handlePaymentStatusWebhook_withSignatureHeader() throws Exception {
        PaymentResponse paymentResponse = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-WH002")
                .status(PaymentStatus.COMPLETED)
                .amount(new BigDecimal("100.00"))
                .build();
        when(paymentService.getPaymentByTransactionId("TXN-WH002")).thenReturn(paymentResponse);
        when(paymentService.updatePaymentStatus(eq(1L), any(PaymentStatus.class))).thenReturn(paymentResponse);

        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction_id", "TXN-WH002");
        payload.put("status", "completed");

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", "some-signature")
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"succeeded", "success", "completed", "captured"})
    @WithMockUser
    void handlePaymentStatusWebhook_completedStatuses(String status) throws Exception {
        PaymentResponse paymentResponse = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-WH003")
                .status(PaymentStatus.COMPLETED)
                .amount(new BigDecimal("100.00"))
                .build();
        when(paymentService.getPaymentByTransactionId("TXN-WH003")).thenReturn(paymentResponse);
        when(paymentService.updatePaymentStatus(eq(1L), any(PaymentStatus.class))).thenReturn(paymentResponse);

        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction_id", "TXN-WH003");
        payload.put("status", status);

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"failed", "declined", "rejected", "error"})
    @WithMockUser
    void handlePaymentStatusWebhook_failedStatuses(String status) throws Exception {
        PaymentResponse paymentResponse = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-WH004")
                .status(PaymentStatus.FAILED)
                .amount(new BigDecimal("100.00"))
                .build();
        when(paymentService.getPaymentByTransactionId("TXN-WH004")).thenReturn(paymentResponse);
        when(paymentService.updatePaymentStatus(eq(1L), any(PaymentStatus.class))).thenReturn(paymentResponse);

        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction_id", "TXN-WH004");
        payload.put("status", status);

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"pending", "requires_action", "processing"})
    @WithMockUser
    void handlePaymentStatusWebhook_processingStatuses(String status) throws Exception {
        PaymentResponse paymentResponse = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-WH005")
                .status(PaymentStatus.PROCESSING)
                .amount(new BigDecimal("100.00"))
                .build();
        when(paymentService.getPaymentByTransactionId("TXN-WH005")).thenReturn(paymentResponse);
        when(paymentService.updatePaymentStatus(eq(1L), any(PaymentStatus.class))).thenReturn(paymentResponse);

        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction_id", "TXN-WH005");
        payload.put("status", status);

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"refunded", "reversed"})
    @WithMockUser
    void handlePaymentStatusWebhook_refundedStatuses(String status) throws Exception {
        PaymentResponse paymentResponse = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-WH006")
                .status(PaymentStatus.REFUNDED)
                .amount(new BigDecimal("100.00"))
                .build();
        when(paymentService.getPaymentByTransactionId("TXN-WH006")).thenReturn(paymentResponse);
        when(paymentService.updatePaymentStatus(eq(1L), any(PaymentStatus.class))).thenReturn(paymentResponse);

        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction_id", "TXN-WH006");
        payload.put("status", status);

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void handlePaymentStatusWebhook_unknownStatus() throws Exception {
        PaymentResponse paymentResponse = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-WH007")
                .status(PaymentStatus.PROCESSING)
                .amount(new BigDecimal("100.00"))
                .build();
        when(paymentService.getPaymentByTransactionId("TXN-WH007")).thenReturn(paymentResponse);
        when(paymentService.updatePaymentStatus(eq(1L), any(PaymentStatus.class))).thenReturn(paymentResponse);

        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction_id", "TXN-WH007");
        payload.put("status", "some_unknown_status");

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void handlePaymentStatusWebhook_processingError() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-WHERR"))
                .thenThrow(new PaymentException("Not found", "PAYMENT_NOT_FOUND"));

        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction_id", "TXN-WHERR");
        payload.put("status", "succeeded");

        mockMvc.perform(post("/api/webhooks/gateway/payment-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warning").value("processing_error"));
    }
}
