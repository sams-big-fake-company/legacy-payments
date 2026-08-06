package com.bigfake.payments.controller;

import com.bigfake.payments.exception.InsufficientFundsException;
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
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Coverage for the PaymentController endpoints not exercised by PaymentControllerTest:
 * transaction lookup, merchant listing, status update and cancellation (PAY-3810).
 */
@WebMvcTest(PaymentController.class)
class PaymentControllerEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    private static PaymentResponse response(long id, String transactionId, String amount) {
        return PaymentResponse.builder()
                .id(id)
                .transactionId(transactionId)
                .merchantId(1L)
                .amount(new BigDecimal(amount))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getPaymentByTransactionId_returns200() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-ABC123")).thenReturn(response(1L, "TXN-ABC123", "10.00"));

        mockMvc.perform(get("/api/v1/payments/transaction/TXN-ABC123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("TXN-ABC123"))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getPaymentByTransactionId_unknown_returns400WithErrorCode() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-NOPE"))
                .thenThrow(new PaymentException("Payment not found: TXN-NOPE", "PAYMENT_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/payments/transaction/TXN-NOPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getPaymentsByMerchant_returnsList() throws Exception {
        when(paymentService.getPaymentsByMerchant(1L))
                .thenReturn(List.of(response(1L, "TXN-1", "10.00"), response(2L, "TXN-2", "20.00")));

        mockMvc.perform(get("/api/v1/payments/merchant/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].transactionId").value("TXN-2"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getPaymentsByMerchant_returnsEmptyList() throws Exception {
        when(paymentService.getPaymentsByMerchant(2L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/payments/merchant/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void updateStatus_returns200() throws Exception {
        PaymentResponse updated = response(1L, "TXN-ABC123", "10.00");
        updated.setStatus(PaymentStatus.REFUNDED);
        when(paymentService.updatePaymentStatus(1L, PaymentStatus.REFUNDED)).thenReturn(updated);

        mockMvc.perform(patch("/api/v1/payments/1/status").param("status", "REFUNDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void updateStatus_invalidTransition_returns400() throws Exception {
        when(paymentService.updatePaymentStatus(1L, PaymentStatus.PENDING))
                .thenThrow(new PaymentException("Cannot transition from COMPLETED to PENDING",
                        "INVALID_STATE_TRANSITION"));

        mockMvc.perform(patch("/api/v1/payments/1/status").param("status", "PENDING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void cancelPayment_returns204() throws Exception {
        doNothing().when(paymentService).cancelPayment(1L);

        mockMvc.perform(post("/api/v1/payments/1/cancel"))
                .andExpect(status().isNoContent());

        verify(paymentService).cancelPayment(1L);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void cancelPayment_nonPending_returns400() throws Exception {
        doThrow(new PaymentException("Can only cancel PENDING payments, current status: COMPLETED", "CANNOT_CANCEL"))
                .when(paymentService).cancelPayment(1L);

        mockMvc.perform(post("/api/v1/payments/1/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("CANNOT_CANCEL"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void insufficientFunds_isReportedAs402() throws Exception {
        when(paymentService.getPaymentById(1L)).thenThrow(new InsufficientFundsException(
                "Merchant daily limit would be exceeded", new BigDecimal("99.99"), new BigDecimal("50.00")));

        mockMvc.perform(get("/api/v1/payments/1"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.requestedAmount").value(99.99))
                .andExpect(jsonPath("$.availableAmount").value(50.00));
    }

    @Test
    void getPaymentsByMerchant_unauthorized_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/payments/merchant/1"))
                .andExpect(status().isUnauthorized());
    }
}
