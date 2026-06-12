package com.bigfake.payments.controller;

import com.bigfake.payments.model.dto.PaymentResponse;
import com.bigfake.payments.model.enums.PaymentStatus;
import com.bigfake.payments.model.enums.PaymentType;
import com.bigfake.payments.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for PaymentController endpoints not covered by PaymentControllerTest.
 */
@WebMvcTest(PaymentController.class)
class PaymentControllerAdditionalTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    private PaymentResponse sampleResponse() {
        return PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-SAMPLE1")
                .merchantId(1L)
                .amount(new BigDecimal("42.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.ACH)
                .build();
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getPaymentByTransactionId_returns200() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-SAMPLE1")).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/payments/transaction/TXN-SAMPLE1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("TXN-SAMPLE1"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getPaymentsByMerchant_returnsList() throws Exception {
        when(paymentService.getPaymentsByMerchant(1L)).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/payments/merchant/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transactionId").value("TXN-SAMPLE1"))
                .andExpect(jsonPath("$[0].merchantId").value(1));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void updateStatus_returns200() throws Exception {
        when(paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED)).thenReturn(sampleResponse());

        mockMvc.perform(patch("/api/v1/payments/1/status").param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void cancelPayment_returns204() throws Exception {
        mockMvc.perform(post("/api/v1/payments/1/cancel"))
                .andExpect(status().isNoContent());

        verify(paymentService).cancelPayment(1L);
    }

    @Test
    void getPayment_unauthorizedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/payments/1"))
                .andExpect(status().isUnauthorized());
    }
}
