package com.bigfake.payments.controller;

import com.bigfake.payments.exception.PaymentException;
import com.bigfake.payments.model.dto.RefundRequest;
import com.bigfake.payments.service.RefundService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web layer tests for {@link RefundController}.
 */
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
    void processRefundReturns201WithTheCreatedRefund() throws Exception {
        RefundRequest request = TestFixtures.refundRequest().build();
        when(refundService.processRefund(any(RefundRequest.class)))
                .thenReturn(TestFixtures.refund().build());

        mockMvc.perform(post("/api/v1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refundId").value("RFD-ABCDEF123456"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(10.00));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void processRefundReturns400ForAnInvalidRequest() throws Exception {
        RefundRequest request = RefundRequest.builder().amount(new BigDecimal("0.00")).build();

        mockMvc.perform(post("/api/v1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void processRefundSurfacesServiceErrorsAsBadRequest() throws Exception {
        when(refundService.processRefund(any(RefundRequest.class)))
                .thenThrow(new PaymentException("Cannot refund payment in status: PENDING",
                        "INVALID_REFUND_STATE"));

        mockMvc.perform(post("/api/v1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestFixtures.refundRequest().build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REFUND_STATE"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getRefundReturnsTheRefund() throws Exception {
        when(refundService.getRefundById(20L)).thenReturn(TestFixtures.refund().build());

        mockMvc.perform(get("/api/v1/refunds/20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundId").value("RFD-ABCDEF123456"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getRefundsForPaymentReturnsEveryRefund() throws Exception {
        when(refundService.getRefundsForPayment(10L)).thenReturn(Arrays.asList(
                TestFixtures.refund().refundId("RFD-ONE").build(),
                TestFixtures.refund().refundId("RFD-TWO").build()));

        mockMvc.perform(get("/api/v1/refunds/payment/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].refundId").value("RFD-ONE"))
                .andExpect(jsonPath("$[1].refundId").value("RFD-TWO"));
    }

    @Test
    void refundEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/refunds/20")).andExpect(status().isUnauthorized());
    }
}
