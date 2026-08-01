package com.bigfake.payments.controller;

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
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
                .refundId("RFD-ABC123")
                .paymentId(5L)
                .amount(new BigDecimal("25.00"))
                .reason("Damaged item")
                .status(PaymentStatus.COMPLETED)
                .initiatedBy("agent-1")
                .build();
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void processRefund_returns201() throws Exception {
        RefundRequest request = RefundRequest.builder()
                .paymentId(5L)
                .amount(new BigDecimal("25.00"))
                .reason("Damaged item")
                .build();
        when(refundService.processRefund(any(RefundRequest.class))).thenReturn(sampleRefund());

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
    void processRefund_invalidRequest_returns400() throws Exception {
        RefundRequest request = RefundRequest.builder()
                .amount(new BigDecimal("0.001"))
                .build();

        mockMvc.perform(post("/api/v1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getRefund_returns200() throws Exception {
        when(refundService.getRefundById(1L)).thenReturn(sampleRefund());

        mockMvc.perform(get("/api/v1/refunds/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundId").value("RFD-ABC123"))
                .andExpect(jsonPath("$.paymentId").value(5));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getRefundsForPayment_returnsList() throws Exception {
        Refund second = Refund.builder()
                .id(2L)
                .refundId("RFD-DEF456")
                .paymentId(5L)
                .amount(new BigDecimal("10.00"))
                .status(PaymentStatus.FAILED)
                .build();
        when(refundService.getRefundsForPayment(5L)).thenReturn(Arrays.asList(sampleRefund(), second));

        mockMvc.perform(get("/api/v1/refunds/payment/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].refundId").value("RFD-ABC123"))
                .andExpect(jsonPath("$[1].refundId").value("RFD-DEF456"));
    }
}
