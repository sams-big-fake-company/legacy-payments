package com.bigfake.payments.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handlePaymentException_returnsBadRequestWithErrorCode() {
        PaymentException ex = new PaymentException("Merchant not found", "MERCHANT_NOT_FOUND");

        ResponseEntity<Map<String, Object>> response = handler.handlePaymentException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("MERCHANT_NOT_FOUND", body.get("error"));
        assertEquals("Merchant not found", body.get("message"));
        assertNotNull(body.get("timestamp"));
    }

    @Test
    void handleInsufficientFunds_returnsPaymentRequiredWithAmounts() {
        InsufficientFundsException ex = new InsufficientFundsException(
                "Daily limit exceeded", new BigDecimal("500.00"), new BigDecimal("100.00"));

        ResponseEntity<Map<String, Object>> response = handler.handleInsufficientFunds(ex);

        assertEquals(HttpStatus.PAYMENT_REQUIRED, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("INSUFFICIENT_FUNDS", body.get("error"));
        assertEquals(new BigDecimal("500.00"), body.get("requestedAmount"));
        assertEquals(new BigDecimal("100.00"), body.get("availableAmount"));
    }

    @Test
    void handleValidation_returnsFieldErrors() throws Exception {
        BindingResult bindingResult = new BindException(new Object(), "paymentRequest");
        ((BindException) bindingResult).addError(
                new FieldError("paymentRequest", "amount", "Amount must be positive"));
        ((BindException) bindingResult).addError(
                new FieldError("paymentRequest", "currency", "Currency is required"));
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(mock(org.springframework.core.MethodParameter.class), bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("VALIDATION_ERROR", body.get("error"));
        @SuppressWarnings("unchecked")
        Map<String, String> fields = (Map<String, String>) body.get("fields");
        assertEquals("Amount must be positive", fields.get("amount"));
        assertEquals("Currency is required", fields.get("currency"));
    }

    @Test
    void handleGeneral_returnsInternalServerError() {
        Exception ex = new IllegalStateException("boom");

        ResponseEntity<Map<String, Object>> response = handler.handleGeneral(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("INTERNAL_ERROR", body.get("error"));
        assertEquals("An unexpected error occurred: boom", body.get("message"));
    }
}
