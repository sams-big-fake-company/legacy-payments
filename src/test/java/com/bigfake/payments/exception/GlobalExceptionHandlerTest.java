package com.bigfake.payments.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the REST error responses produced by GlobalExceptionHandler.
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
                handler.handlePaymentException(new PaymentException("Merchant is inactive", "MERCHANT_INACTIVE"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("MERCHANT_INACTIVE", response.getBody().get("error"));
        assertEquals("Merchant is inactive", response.getBody().get("message"));
        assertNotNull(response.getBody().get("timestamp"));
    }

    @Test
    void handleInsufficientFunds_returns402WithAmounts() {
        InsufficientFundsException exception = new InsufficientFundsException(
                "Merchant daily limit would be exceeded", new BigDecimal("500.00"), new BigDecimal("200.00"));

        ResponseEntity<Map<String, Object>> response = handler.handleInsufficientFunds(exception);

        assertEquals(HttpStatus.PAYMENT_REQUIRED, response.getStatusCode());
        assertEquals("INSUFFICIENT_FUNDS", response.getBody().get("error"));
        assertEquals(new BigDecimal("500.00"), response.getBody().get("requestedAmount"));
        assertEquals(new BigDecimal("200.00"), response.getBody().get("availableAmount"));
    }

    @Test
    void handleValidation_returns400WithFieldErrors() throws NoSuchMethodException {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "paymentRequest");
        bindingResult.addError(new FieldError("paymentRequest", "amount", "Amount must be at least 0.01"));
        bindingResult.addError(new FieldError("paymentRequest", "currency", "Currency is required"));
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
                new org.springframework.core.MethodParameter(
                        GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyHandlerMethod", String.class), 0),
                bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("VALIDATION_ERROR", response.getBody().get("error"));
        assertEquals(Map.of(
                        "amount", "Amount must be at least 0.01",
                        "currency", "Currency is required"),
                response.getBody().get("fields"));
    }

    @Test
    void handleValidation_returnsEmptyFieldMapWhenThereAreNoFieldErrors() throws NoSuchMethodException {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "paymentRequest");
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
                new org.springframework.core.MethodParameter(
                        GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyHandlerMethod", String.class), 0),
                bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(exception);

        assertEquals(Map.of(), response.getBody().get("fields"));
    }

    @Test
    void handleGeneral_returns500WithGenericMessage() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleGeneral(new IllegalStateException("database unavailable"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getBody().get("error"));
        assertEquals("An unexpected error occurred: database unavailable", response.getBody().get("message"));
    }

    @SuppressWarnings("unused")
    private void dummyHandlerMethod(String argument) {
        // Only used to build a MethodParameter for MethodArgumentNotValidException.
    }
}
