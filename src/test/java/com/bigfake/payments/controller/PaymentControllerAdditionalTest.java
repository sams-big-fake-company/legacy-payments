package com.bigfake.payments.controller;

import com.bigfake.payments.exception.PaymentException;
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
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
class PaymentControllerAdditionalTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getPaymentByTransactionId_returns200() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-BY-TID")
                .merchantId(1L)
                .amount(new BigDecimal("75.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .createdAt(LocalDateTime.now())
                .build();
        when(paymentService.getPaymentByTransactionId("TXN-BY-TID")).thenReturn(response);

        mockMvc.perform(get("/api/v1/payments/transaction/TXN-BY-TID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("TXN-BY-TID"))
                .andExpect(jsonPath("$.amount").value(75.00));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getPaymentByTransactionId_notFound() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-MISSING"))
                .thenThrow(new PaymentException("Not found", "PAYMENT_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/payments/transaction/TXN-MISSING"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getPaymentsByMerchant_returnsList() throws Exception {
        PaymentResponse p1 = PaymentResponse.builder()
                .id(1L).transactionId("TXN-M1").merchantId(1L)
                .amount(new BigDecimal("10.00")).status(PaymentStatus.COMPLETED).build();
        PaymentResponse p2 = PaymentResponse.builder()
                .id(2L).transactionId("TXN-M2").merchantId(1L)
                .amount(new BigDecimal("20.00")).status(PaymentStatus.PENDING).build();
        when(paymentService.getPaymentsByMerchant(1L)).thenReturn(List.of(p1, p2));

        mockMvc.perform(get("/api/v1/payments/merchant/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getPaymentsByMerchant_emptyList() throws Exception {
        when(paymentService.getPaymentsByMerchant(1L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/payments/merchant/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void updateStatus_returns200() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .id(1L)
                .transactionId("TXN-UPD001")
                .status(PaymentStatus.COMPLETED)
                .amount(new BigDecimal("100.00"))
                .build();
        when(paymentService.updatePaymentStatus(eq(1L), eq(PaymentStatus.COMPLETED)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/v1/payments/1/status")
                        .with(csrf())
                        .param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void cancelPayment_returns204() throws Exception {
        mockMvc.perform(post("/api/v1/payments/1/cancel")
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void cancelPayment_notPending_returnsBadRequest() throws Exception {
        doThrow(new PaymentException("Cannot cancel", "CANNOT_CANCEL"))
                .when(paymentService).cancelPayment(1L);

        mockMvc.perform(post("/api/v1/payments/1/cancel")
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPayment_unauthorized_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/payments/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getPaymentsByMerchant_unauthorized_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/payments/merchant/1"))
                .andExpect(status().isUnauthorized());
    }
}
