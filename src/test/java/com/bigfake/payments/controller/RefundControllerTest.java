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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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

    private Refund sampleRefund() {
        return Refund.builder()
                .id(1L)
                .refundId("RFD-SAMPLE123")
                .paymentId(2L)
                .amount(new BigDecimal("25.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void processRefund_returns201() throws Exception {
        RefundRequest request = RefundRequest.builder()
                .paymentId(2L)
                .amount(new BigDecimal("25.00"))
                .reason("Customer request")
                .build();
        when(refundService.processRefund(any(RefundRequest.class))).thenReturn(sampleRefund());

        mockMvc.perform(post("/api/v1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refundId").value("RFD-SAMPLE123"))
                .andExpect(jsonPath("$.amount").value(25.00));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void processRefund_invalidRequest_returns400() throws Exception {
        RefundRequest request = RefundRequest.builder()
                .amount(new BigDecimal("0.001")) // below minimum, missing paymentId
                .build();

        mockMvc.perform(post("/api/v1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void processRefund_unauthorized_returns401() throws Exception {
        RefundRequest request = RefundRequest.builder()
                .paymentId(2L)
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
        when(refundService.getRefundById(1L)).thenReturn(sampleRefund());

        mockMvc.perform(get("/api/v1/refunds/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundId").value("RFD-SAMPLE123"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getRefund_notFound_returns400FromHandler() throws Exception {
        when(refundService.getRefundById(99L))
                .thenThrow(new PaymentException("Refund not found: 99", "REFUND_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/refunds/99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("REFUND_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getRefundsForPayment_returnsList() throws Exception {
        when(refundService.getRefundsForPayment(2L)).thenReturn(List.of(sampleRefund()));

        mockMvc.perform(get("/api/v1/refunds/payment/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].refundId").value("RFD-SAMPLE123"));
    }
}
