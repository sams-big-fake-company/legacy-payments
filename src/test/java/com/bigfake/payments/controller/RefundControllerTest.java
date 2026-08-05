package com.bigfake.payments.controller;

import com.bigfake.payments.exception.PaymentException;
import com.bigfake.payments.model.dto.RefundRequest;
import com.bigfake.payments.model.entity.Refund;
import com.bigfake.payments.model.enums.PaymentStatus;
import com.bigfake.payments.service.RefundService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller tests for RefundController.
 */
@WebMvcTest(RefundController.class)
class RefundControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RefundService refundService;

    private static Refund refund(Long id, String refundId, String amount) {
        return Refund.builder()
                .id(id)
                .refundId(refundId)
                .paymentId(7L)
                .amount(new BigDecimal(amount))
                .status(PaymentStatus.COMPLETED)
                .build();
    }

    private static RefundRequest validRequest() {
        return RefundRequest.builder()
                .paymentId(7L)
                .amount(new BigDecimal("40.00"))
                .reason("Customer returned item")
                .build();
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void processRefund_returns201() throws Exception {
        when(refundService.processRefund(any(RefundRequest.class))).thenReturn(refund(1L, "RFD-ABC123", "40.00"));

        mockMvc.perform(post("/api/v1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refundId").value("RFD-ABC123"))
                .andExpect(jsonPath("$.amount").value(40.00))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void processRefund_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());

        verify(refundService, never()).processRefund(any(RefundRequest.class));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void processRefund_missingPaymentId_returns400() throws Exception {
        RefundRequest request = validRequest();
        request.setPaymentId(null);

        mockMvc.perform(post("/api/v1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.paymentId").value("Payment ID is required"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void processRefund_amountBelowMinimum_returns400() throws Exception {
        RefundRequest request = validRequest();
        request.setAmount(new BigDecimal("0.00"));

        mockMvc.perform(post("/api/v1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.amount").value("Refund amount must be at least 0.01"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void processRefund_serviceRejectsRefund_returns400WithErrorCode() throws Exception {
        when(refundService.processRefund(any(RefundRequest.class)))
                .thenThrow(new PaymentException("Cannot refund payment in status: PENDING", "INVALID_REFUND_STATE"));

        mockMvc.perform(post("/api/v1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REFUND_STATE"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getRefund_returns200() throws Exception {
        when(refundService.getRefundById(1L)).thenReturn(refund(1L, "RFD-ABC123", "40.00"));

        mockMvc.perform(get("/api/v1/refunds/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundId").value("RFD-ABC123"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getRefundsForPayment_returnsList() throws Exception {
        when(refundService.getRefundsForPayment(7L))
                .thenReturn(List.of(refund(1L, "RFD-ABC123", "40.00"), refund(2L, "RFD-DEF456", "10.00")));

        mockMvc.perform(get("/api/v1/refunds/payment/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].refundId").value("RFD-DEF456"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getRefundsForPayment_noRefunds_returnsEmptyList() throws Exception {
        when(refundService.getRefundsForPayment(7L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/refunds/payment/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
