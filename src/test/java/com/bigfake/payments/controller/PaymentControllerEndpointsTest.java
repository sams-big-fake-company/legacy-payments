package com.bigfake.payments.controller;

import com.bigfake.payments.exception.InsufficientFundsException;
import com.bigfake.payments.exception.PaymentException;
import com.bigfake.payments.model.dto.PaymentResponse;
import com.bigfake.payments.model.enums.PaymentStatus;
import com.bigfake.payments.model.enums.PaymentType;
import com.bigfake.payments.service.PaymentService;
import com.bigfake.payments.testsupport.TestFixtures;
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
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web layer tests for the {@link PaymentController} endpoints and error mapping
 * that the existing PaymentControllerTest does not cover.
 */
@WebMvcTest(PaymentController.class)
class PaymentControllerEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

    private PaymentResponse response() {
        return PaymentResponse.builder()
                .id(10L)
                .transactionId("TXN-0123456789ABCDEF")
                .merchantId(TestFixtures.MERCHANT_ID)
                .amount(new BigDecimal("99.99"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.CREDIT_CARD)
                .build();
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getPaymentByTransactionIdReturnsThePayment() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-0123456789ABCDEF")).thenReturn(response());

        mockMvc.perform(get("/api/v1/payments/transaction/TXN-0123456789ABCDEF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getPaymentsByMerchantReturnsTheList() throws Exception {
        when(paymentService.getPaymentsByMerchant(1L)).thenReturn(Arrays.asList(response(), response()));

        mockMvc.perform(get("/api/v1/payments/merchant/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getPaymentsByMerchantReturnsAnEmptyList() throws Exception {
        when(paymentService.getPaymentsByMerchant(2L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/payments/merchant/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void updateStatusPassesTheRequestedStatusToTheService() throws Exception {
        when(paymentService.updatePaymentStatus(10L, PaymentStatus.REFUNDED)).thenReturn(response());

        mockMvc.perform(patch("/api/v1/payments/10/status").param("status", "REFUNDED"))
                .andExpect(status().isOk());

        verify(paymentService).updatePaymentStatus(10L, PaymentStatus.REFUNDED);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void cancelPaymentReturns204() throws Exception {
        mockMvc.perform(post("/api/v1/payments/10/cancel"))
                .andExpect(status().isNoContent());

        verify(paymentService).cancelPayment(10L);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void paymentErrorsAreReturnedAsBadRequestWithTheErrorCode() throws Exception {
        doThrow(new PaymentException("Payment not found: 999", "PAYMENT_NOT_FOUND"))
                .when(paymentService).getPaymentById(999L);

        mockMvc.perform(get("/api/v1/payments/999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PAYMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Payment not found: 999"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void limitBreachesAreReturnedAsPaymentRequiredWithTheAmounts() throws Exception {
        when(paymentService.processPayment(any())).thenThrow(new InsufficientFundsException(
                "Merchant daily limit would be exceeded",
                new BigDecimal("100.00"),
                new BigDecimal("25.00")));

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestFixtures.creditCardRequest().build())))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.requestedAmount").value(100.00))
                .andExpect(jsonPath("$.availableAmount").value(25.00));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void unexpectedFailuresAreReturnedAsInternalServerError() throws Exception {
        when(paymentService.processPayment(any()))
                .thenThrow(new IllegalStateException("connection pool exhausted"));

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestFixtures.creditCardRequest().build())))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"));
    }

    @Test
    void everyPaymentEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/payments/10")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/payments/merchant/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/payments/10/cancel")).andExpect(status().isUnauthorized());
    }
}
