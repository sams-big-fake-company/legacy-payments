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
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
                .transactionId("TXN-ABCDEF")
                .merchantId(1L)
                .amount(new BigDecimal("75.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .createdAt(LocalDateTime.now())
                .build();

        when(paymentService.getPaymentByTransactionId("TXN-ABCDEF")).thenReturn(response);

        mockMvc.perform(get("/api/v1/payments/transaction/TXN-ABCDEF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("TXN-ABCDEF"))
                .andExpect(jsonPath("$.amount").value(75.00));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getPaymentsByMerchant_returns200() throws Exception {
        PaymentResponse p1 = PaymentResponse.builder()
                .id(1L).transactionId("TXN-001").merchantId(1L)
                .amount(new BigDecimal("50.00")).status(PaymentStatus.COMPLETED)
                .build();
        PaymentResponse p2 = PaymentResponse.builder()
                .id(2L).transactionId("TXN-002").merchantId(1L)
                .amount(new BigDecimal("75.00")).status(PaymentStatus.PENDING)
                .build();

        when(paymentService.getPaymentsByMerchant(1L)).thenReturn(List.of(p1, p2));

        mockMvc.perform(get("/api/v1/payments/merchant/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].transactionId").value("TXN-001"))
                .andExpect(jsonPath("$[1].transactionId").value("TXN-002"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void updateStatus_returns200() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .id(1L).transactionId("TXN-STATUS")
                .status(PaymentStatus.COMPLETED)
                .build();

        when(paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED)).thenReturn(response);

        mockMvc.perform(patch("/api/v1/payments/1/status?status=COMPLETED"))
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
    void getPaymentByTransactionId_unauthorized_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/payments/transaction/TXN-TEST"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cancelPayment_unauthorized_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/payments/1/cancel"))
                .andExpect(status().isUnauthorized());
    }
}
