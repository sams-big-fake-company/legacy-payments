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
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RefundController.class)
class RefundControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RefundService refundService;

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void processRefund_returns201() throws Exception {
        RefundRequest request = RefundRequest.builder()
                .paymentId(1L)
                .amount(new BigDecimal("25.00"))
                .reason("Customer request")
                .build();

        Refund refund = Refund.builder()
                .id(1L)
                .refundId("RFD-TEST001")
                .paymentId(1L)
                .amount(new BigDecimal("25.00"))
                .reason("Customer request")
                .status(PaymentStatus.COMPLETED)
                .build();

        when(refundService.processRefund(any(RefundRequest.class))).thenReturn(refund);

        mockMvc.perform(post("/api/v1/refunds")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refundId").value("RFD-TEST001"))
                .andExpect(jsonPath("$.amount").value(25.00));
    }

    @Test
    void processRefund_unauthorized_returns401() throws Exception {
        RefundRequest request = RefundRequest.builder()
                .paymentId(1L)
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
        Refund refund = Refund.builder()
                .id(1L)
                .refundId("RFD-GET001")
                .paymentId(1L)
                .amount(new BigDecimal("50.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
        when(refundService.getRefundById(1L)).thenReturn(refund);

        mockMvc.perform(get("/api/v1/refunds/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundId").value("RFD-GET001"))
                .andExpect(jsonPath("$.amount").value(50.00));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getRefund_notFound() throws Exception {
        when(refundService.getRefundById(999L))
                .thenThrow(new PaymentException("Not found", "REFUND_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/refunds/999"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getRefundsForPayment_returnsList() throws Exception {
        Refund refund1 = Refund.builder()
                .id(1L).refundId("RFD-LIST001").paymentId(1L)
                .amount(new BigDecimal("10.00")).status(PaymentStatus.COMPLETED).build();
        Refund refund2 = Refund.builder()
                .id(2L).refundId("RFD-LIST002").paymentId(1L)
                .amount(new BigDecimal("20.00")).status(PaymentStatus.COMPLETED).build();
        when(refundService.getRefundsForPayment(1L)).thenReturn(List.of(refund1, refund2));

        mockMvc.perform(get("/api/v1/refunds/payment/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getRefundsForPayment_emptyList() throws Exception {
        when(refundService.getRefundsForPayment(1L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/refunds/payment/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
