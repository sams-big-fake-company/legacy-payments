package com.bigfake.payments.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for GlobalExceptionHandler.
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handlePaymentException_returns400WithErrorCode() {
        ResponseEntity<Map<String, Object>> response =
                handler.handlePaymentException(new PaymentException("Merchant not found: 5", "MERCHANT_NOT_FOUND"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("MERCHANT_NOT_FOUND", body.get("error"));
        assertEquals("Merchant not found: 5", body.get("message"));
        assertNotNull(body.get("timestamp"));
    }

    @Test
    void handleInsufficientFunds_returns402WithAmounts() {
        InsufficientFundsException exception = new InsufficientFundsException(
                "Merchant daily limit would be exceeded", new BigDecimal("500.00"), new BigDecimal("120.50"));

        ResponseEntity<Map<String, Object>> response = handler.handleInsufficientFunds(exception);

        assertEquals(HttpStatus.PAYMENT_REQUIRED, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("INSUFFICIENT_FUNDS", body.get("error"));
        assertEquals(new BigDecimal("500.00"), body.get("requestedAmount"));
        assertEquals(new BigDecimal("120.50"), body.get("availableAmount"));
    }

    @Test
    void handleValidation_returns400WithFieldErrors() throws NoSuchMethodException {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "paymentRequest");
        bindingResult.addError(new org.springframework.validation.FieldError(
                "paymentRequest", "currency", "Currency is required"));
        bindingResult.addError(new org.springframework.validation.FieldError(
                "paymentRequest", "amount", "Amount must be at least 0.01"));
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
                new MethodParameter(GlobalExceptionHandlerTest.class.getDeclaredMethod("handlerTarget", String.class), 0),
                bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("VALIDATION_ERROR", body.get("error"));
        assertEquals(Map.of("currency", "Currency is required", "amount", "Amount must be at least 0.01"),
                body.get("fields"));
    }

    @Test
    void handleValidation_noFieldErrors_returnsEmptyFieldMap() throws NoSuchMethodException {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "paymentRequest");
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
                new MethodParameter(GlobalExceptionHandlerTest.class.getDeclaredMethod("handlerTarget", String.class), 0),
                bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(exception);

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(Map.of(), body.get("fields"));
    }

    @Test
    void handleGeneral_returns500WithGenericMessage() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleGeneral(new IllegalStateException("connection pool exhausted"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("INTERNAL_ERROR", body.get("error"));
        assertTrue(String.valueOf(body.get("message")).contains("connection pool exhausted"));
    }

    @SuppressWarnings("unused")
    private void handlerTarget(String argument) {
        // Reflection target used to build a MethodParameter for MethodArgumentNotValidException.
    }
}
