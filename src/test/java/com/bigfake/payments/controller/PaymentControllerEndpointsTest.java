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
import java.util.Arrays;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for PaymentController endpoints not covered by
 * PaymentControllerTest (lookup by transaction ID, merchant listing,
 * status updates, and cancellation).
 */
@WebMvcTest(PaymentController.class)
class PaymentControllerEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    private PaymentResponse sampleResponse(String transactionId) {
        return PaymentResponse.builder()
                .id(1L)
                .transactionId(transactionId)
                .merchantId(2L)
                .amount(new BigDecimal("75.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.DEBIT)
                .build();
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getPaymentByTransactionId_returns200() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-LOOKUP"))
                .thenReturn(sampleResponse("TXN-LOOKUP"));

        mockMvc.perform(get("/api/v1/payments/transaction/TXN-LOOKUP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("TXN-LOOKUP"))
                .andExpect(jsonPath("$.merchantId").value(2));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getPaymentsByMerchant_returnsList() throws Exception {
        when(paymentService.getPaymentsByMerchant(2L))
                .thenReturn(Arrays.asList(sampleResponse("TXN-1"), sampleResponse("TXN-2")));

        mockMvc.perform(get("/api/v1/payments/merchant/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].transactionId").value("TXN-1"))
                .andExpect(jsonPath("$[1].transactionId").value("TXN-2"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void updateStatus_returns200() throws Exception {
        when(paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED))
                .thenReturn(sampleResponse("TXN-UPDATED"));

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
    void getPayment_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/payments/transaction/TXN-LOOKUP"))
                .andExpect(status().isUnauthorized());
    }
}
