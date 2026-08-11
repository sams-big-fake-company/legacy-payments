package com.bigfake.payments.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
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
                handler.handlePaymentException(new PaymentException("Merchant not found: 1", "MERCHANT_NOT_FOUND"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("MERCHANT_NOT_FOUND", body.get("error"));
        assertEquals("Merchant not found: 1", body.get("message"));
        assertNotNull(body.get("timestamp"));
    }

    @Test
    void handlePaymentException_defaultErrorCode_isPaymentError() {
        ResponseEntity<Map<String, Object>> response =
                handler.handlePaymentException(new PaymentException("boom"));

        assertEquals("PAYMENT_ERROR", response.getBody().get("error"));
    }

    @Test
    void handleInsufficientFunds_returns402WithAmounts() {
        InsufficientFundsException exception = new InsufficientFundsException(
                "ACH daily limit exceeded", new BigDecimal("500.00"), new BigDecimal("120.00"));

        ResponseEntity<Map<String, Object>> response = handler.handleInsufficientFunds(exception);

        assertEquals(HttpStatus.PAYMENT_REQUIRED, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("INSUFFICIENT_FUNDS", body.get("error"));
        assertEquals("ACH daily limit exceeded", body.get("message"));
        assertEquals(new BigDecimal("500.00"), body.get("requestedAmount"));
        assertEquals(new BigDecimal("120.00"), body.get("availableAmount"));
    }

    @Test
    void handleValidation_returns400WithFieldErrors() throws NoSuchMethodException {
        BindingResult withFields = new BeanPropertyBindingResult(new Object(), "paymentRequest");
        withFields.addError(new FieldError("paymentRequest", "amount", "Amount is required"));
        withFields.addError(new FieldError("paymentRequest", "currency", "Currency is required"));

        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
                new MethodParameter(
                        GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyEndpoint", String.class), 0),
                withFields);

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("VALIDATION_ERROR", body.get("error"));
        assertEquals(Map.of("amount", "Amount is required", "currency", "Currency is required"),
                body.get("fields"));
    }

    @Test
    void handleValidation_withoutFieldErrors_returnsEmptyFieldMap() throws NoSuchMethodException {
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
                new MethodParameter(
                        GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyEndpoint", String.class), 0),
                new BeanPropertyBindingResult(new Object(), "paymentRequest"));

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(exception);

        assertEquals(Map.of(), response.getBody().get("fields"));
    }

    @Test
    void handleGeneral_returns500WithGenericMessage() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleGeneral(new IllegalStateException("database unavailable"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("INTERNAL_ERROR", body.get("error"));
        assertTrue(((String) body.get("message")).contains("database unavailable"));
    }

    @SuppressWarnings("unused")
    private void dummyEndpoint(String request) {
        // Target for MethodParameter in validation tests.
    }
}
