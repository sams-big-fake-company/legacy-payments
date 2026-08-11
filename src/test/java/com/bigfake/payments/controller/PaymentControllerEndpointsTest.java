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
import java.util.Arrays;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller tests for the PaymentController lookup, status and cancel endpoints.
 */
@WebMvcTest(PaymentController.class)
class PaymentControllerEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    private static PaymentResponse response(long id, String transactionId, PaymentStatus status) {
        return PaymentResponse.builder()
                .id(id)
                .transactionId(transactionId)
                .merchantId(1L)
                .amount(new BigDecimal("30.00"))
                .currency("USD")
                .status(status)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getPaymentByTransactionId_returns200() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-ABC"))
                .thenReturn(response(1L, "TXN-ABC", PaymentStatus.COMPLETED));

        mockMvc.perform(get("/api/v1/payments/transaction/TXN-ABC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("TXN-ABC"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getPaymentByTransactionId_unknown_returns400WithErrorCode() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-MISSING"))
                .thenThrow(new PaymentException("Payment not found: TXN-MISSING", "PAYMENT_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/payments/transaction/TXN-MISSING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getPaymentsByMerchant_returnsList() throws Exception {
        when(paymentService.getPaymentsByMerchant(1L)).thenReturn(Arrays.asList(
                response(1L, "TXN-1", PaymentStatus.COMPLETED),
                response(2L, "TXN-2", PaymentStatus.FAILED)));

        mockMvc.perform(get("/api/v1/payments/merchant/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].status").value("FAILED"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void updateStatus_returns200WithUpdatedPayment() throws Exception {
        when(paymentService.updatePaymentStatus(1L, PaymentStatus.REFUNDED))
                .thenReturn(response(1L, "TXN-1", PaymentStatus.REFUNDED));

        mockMvc.perform(patch("/api/v1/payments/1/status").param("status", "REFUNDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void updateStatus_invalidTransition_returns400() throws Exception {
        when(paymentService.updatePaymentStatus(1L, PaymentStatus.PENDING))
                .thenThrow(new PaymentException("Cannot transition", "INVALID_STATE_TRANSITION"));

        mockMvc.perform(patch("/api/v1/payments/1/status").param("status", "PENDING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_STATE_TRANSITION"));
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
    void cancelPayment_nonPending_returns400() throws Exception {
        doThrow(new PaymentException("Can only cancel PENDING payments", "CANNOT_CANCEL"))
                .when(paymentService).cancelPayment(1L);

        mockMvc.perform(post("/api/v1/payments/1/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("CANNOT_CANCEL"));
    }

    @Test
    void cancelPayment_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/payments/1/cancel"))
                .andExpect(status().isUnauthorized());
    }
}
