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

    private Refund refund(String refundId, String amount) {
        return Refund.builder()
                .id(1L)
                .refundId(refundId)
                .paymentId(10L)
                .amount(new BigDecimal(amount))
                .reason("Customer returned item")
                .status(PaymentStatus.COMPLETED)
                .initiatedBy("agent-42")
                .build();
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void processRefund_returns201() throws Exception {
        RefundRequest request = RefundRequest.builder()
                .paymentId(10L)
                .amount(new BigDecimal("25.00"))
                .reason("Customer returned item")
                .build();
        when(refundService.processRefund(any(RefundRequest.class))).thenReturn(refund("RFD-ABC123", "25.00"));

        mockMvc.perform(post("/api/v1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refundId").value("RFD-ABC123"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(25.00));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void processRefund_invalidRequestReturns400() throws Exception {
        RefundRequest request = RefundRequest.builder()
                .amount(new BigDecimal("0.00"))
                .build();

        mockMvc.perform(post("/api/v1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void processRefund_serviceRejectionReturns400() throws Exception {
        RefundRequest request = RefundRequest.builder()
                .paymentId(10L)
                .amount(new BigDecimal("25.00"))
                .build();
        when(refundService.processRefund(any(RefundRequest.class)))
                .thenThrow(new PaymentException("Cannot refund payment in status: PENDING", "INVALID_REFUND_STATE"));

        mockMvc.perform(post("/api/v1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REFUND_STATE"));
    }

    @Test
    void processRefund_unauthorizedReturns401() throws Exception {
        RefundRequest request = RefundRequest.builder()
                .paymentId(10L)
                .amount(new BigDecimal("25.00"))
                .build();

        mockMvc.perform(post("/api/v1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getRefund_returns200() throws Exception {
        when(refundService.getRefundById(1L)).thenReturn(refund("RFD-XYZ789", "40.00"));

        mockMvc.perform(get("/api/v1/refunds/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundId").value("RFD-XYZ789"))
                .andExpect(jsonPath("$.paymentId").value(10));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getRefund_notFoundReturns400() throws Exception {
        when(refundService.getRefundById(404L))
                .thenThrow(new PaymentException("Refund not found: 404", "REFUND_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/refunds/404"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("REFUND_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getRefundsForPayment_returnsList() throws Exception {
        when(refundService.getRefundsForPayment(10L))
                .thenReturn(List.of(refund("RFD-1", "10.00"), refund("RFD-2", "15.00")));

        mockMvc.perform(get("/api/v1/refunds/payment/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].refundId").value("RFD-1"))
                .andExpect(jsonPath("$[1].amount").value(15.00));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getRefundsForPayment_returnsEmptyList() throws Exception {
        when(refundService.getRefundsForPayment(11L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/refunds/payment/11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
