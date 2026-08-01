package com.bigfake.payments.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handlePaymentException_returnsBadRequest() {
        PaymentException ex = new PaymentException("Test error", "TEST_CODE");

        ResponseEntity<Map<String, Object>> response = handler.handlePaymentException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("TEST_CODE", response.getBody().get("error"));
        assertEquals("Test error", response.getBody().get("message"));
        assertNotNull(response.getBody().get("timestamp"));
    }

    @Test
    void handlePaymentException_defaultErrorCode() {
        PaymentException ex = new PaymentException("Default error");

        ResponseEntity<Map<String, Object>> response = handler.handlePaymentException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("PAYMENT_ERROR", response.getBody().get("error"));
    }

    @Test
    void handleInsufficientFunds_returnsPaymentRequired() {
        InsufficientFundsException ex = new InsufficientFundsException(
                "Daily limit exceeded",
                new BigDecimal("500.00"),
                new BigDecimal("100.00"));

        ResponseEntity<Map<String, Object>> response = handler.handleInsufficientFunds(ex);

        assertEquals(HttpStatus.PAYMENT_REQUIRED, response.getStatusCode());
        assertEquals("INSUFFICIENT_FUNDS", response.getBody().get("error"));
        assertEquals(new BigDecimal("500.00"), response.getBody().get("requestedAmount"));
        assertEquals(new BigDecimal("100.00"), response.getBody().get("availableAmount"));
        assertNotNull(response.getBody().get("timestamp"));
    }

    @Test
    void handleValidation_returnsBadRequestWithFieldErrors() {
        Object target = new Object();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "paymentRequest");
        bindingResult.addError(new FieldError("paymentRequest", "amount", "Amount is required"));
        bindingResult.addError(new FieldError("paymentRequest", "currency", "Currency is required"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("VALIDATION_ERROR", response.getBody().get("error"));
        @SuppressWarnings("unchecked")
        Map<String, String> fieldErrors = (Map<String, String>) response.getBody().get("fields");
        assertEquals("Amount is required", fieldErrors.get("amount"));
        assertEquals("Currency is required", fieldErrors.get("currency"));
    }

    @Test
    void handleGeneral_returnsInternalServerError() {
        RuntimeException ex = new RuntimeException("Something went wrong");

        ResponseEntity<Map<String, Object>> response = handler.handleGeneral(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getBody().get("error"));
        assertTrue(response.getBody().get("message").toString().contains("Something went wrong"));
        assertNotNull(response.getBody().get("timestamp"));
    }
}
