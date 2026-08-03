package com.bigfake.payments.controller;

import com.bigfake.payments.exception.InsufficientFundsException;
import com.bigfake.payments.exception.PaymentException;
import com.bigfake.payments.model.dto.PaymentRequest;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for PaymentController.
 *
 * TODO: PAY-3810 - Add tests for error scenarios
 * TODO: PAY-3811 - Add tests for pagination
 */
@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void createPayment_returns201() throws Exception {
        // Given
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("49.99"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .customerEmail("test@example.com")
                .build();

        PaymentResponse response = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-ABC123")
                .merchantId(1L)
                .amount(new BigDecimal("49.99"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .feeAmount(new BigDecimal("1.45"))
                .netAmount(new BigDecimal("48.54"))
                .createdAt(LocalDateTime.now())
                .build();

        when(paymentService.processPayment(any(PaymentRequest.class))).thenReturn(response);

        // When/Then
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId").value("TXN-ABC123"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(49.99));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getPayment_returns200() throws Exception {
        // Given
        PaymentResponse response = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-XYZ789")
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.WIRE)
                .createdAt(LocalDateTime.now())
                .build();

        when(paymentService.getPaymentById(1L)).thenReturn(response);

        // When/Then
        mockMvc.perform(get("/api/v1/payments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("TXN-XYZ789"))
                .andExpect(jsonPath("$.amount").value(100.00));
    }

    @Test
    void createPayment_unauthorized_returns401() throws Exception {
        // Given - no auth
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("49.99"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .build();

        // When/Then
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void createPayment_invalidRequest_returns400() throws Exception {
        // Given - missing required fields
        PaymentRequest request = PaymentRequest.builder()
                .amount(new BigDecimal("-1.00")) // Invalid amount
                .build();

        // When/Then
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    private static PaymentResponse paymentResponse(Long id, String transactionId, PaymentStatus status) {
        return PaymentResponse.builder()
                .id(id)
                .transactionId(transactionId)
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(status)
                .paymentType(PaymentType.CREDIT_CARD)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void createPayment_serviceRejects_returns400WithErrorCode() throws Exception {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("49.99"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .build();
        when(paymentService.processPayment(any(PaymentRequest.class)))
                .thenThrow(new PaymentException("Merchant is inactive: MERCH001", "MERCHANT_INACTIVE"));

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MERCHANT_INACTIVE"))
                .andExpect(jsonPath("$.message").value("Merchant is inactive: MERCH001"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void createPayment_overLimit_returns402WithAmounts() throws Exception {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("49.99"))
                .currency("USD")
                .paymentType(PaymentType.ACH)
                .build();
        when(paymentService.processPayment(any(PaymentRequest.class)))
                .thenThrow(new InsufficientFundsException("ACH daily limit exceeded",
                        new BigDecimal("49.99"), new BigDecimal("10.00")));

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.availableAmount").value(10.00));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getPaymentByTransactionId_returns200() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-ABC123"))
                .thenReturn(paymentResponse(1L, "TXN-ABC123", PaymentStatus.COMPLETED));

        mockMvc.perform(get("/api/v1/payments/transaction/TXN-ABC123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("TXN-ABC123"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getPaymentsByMerchant_returnsTheList() throws Exception {
        when(paymentService.getPaymentsByMerchant(1L)).thenReturn(List.of(
                paymentResponse(1L, "TXN-A", PaymentStatus.COMPLETED),
                paymentResponse(2L, "TXN-B", PaymentStatus.FAILED)));

        mockMvc.perform(get("/api/v1/payments/merchant/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].transactionId").value("TXN-B"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void updateStatus_returns200() throws Exception {
        when(paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED))
                .thenReturn(paymentResponse(1L, "TXN-ABC123", PaymentStatus.COMPLETED));

        mockMvc.perform(patch("/api/v1/payments/1/status")
                        .param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void updateStatus_unknownStatus_returns500() throws Exception {
        // An unparseable enum value is not handled by a dedicated handler, so it
        // falls through to the catch-all handler.
        mockMvc.perform(patch("/api/v1/payments/1/status")
                        .param("status", "NOT_A_STATUS"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void cancelPayment_returns204() throws Exception {
        mockMvc.perform(post("/api/v1/payments/1/cancel"))
                .andExpect(status().isNoContent());

        verify(paymentService).cancelPayment(1L);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void cancelPayment_notCancellable_returns400() throws Exception {
        doThrow(new PaymentException("Can only cancel PENDING payments, current status: COMPLETED", "CANNOT_CANCEL"))
                .when(paymentService).cancelPayment(1L);

        mockMvc.perform(post("/api/v1/payments/1/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("CANNOT_CANCEL"));
    }

    @Test
    void getPayment_unauthorized_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/payments/1"))
                .andExpect(status().isUnauthorized());
    }
}
