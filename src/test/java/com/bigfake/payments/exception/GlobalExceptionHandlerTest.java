package com.bigfake.payments.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.core.MethodParameter;

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

    /** Target of the {@link MethodParameter} used to build a binding failure. */
    @SuppressWarnings("unused")
    private void annotatedMethod(String argument) {
        // no-op: only used to obtain a MethodParameter for MethodArgumentNotValidException
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
    void handlePaymentException_usesDefaultErrorCodeWhenNoneProvided() {
        ResponseEntity<Map<String, Object>> response =
                handler.handlePaymentException(new PaymentException("boom"));

        assertEquals("PAYMENT_ERROR", requireBody(response).get("error"));
    }

    @Test
    void handleInsufficientFunds_returns402WithAmountDetails() {
        InsufficientFundsException exception = new InsufficientFundsException(
                "ACH daily limit exceeded", new BigDecimal("500.00"), new BigDecimal("120.00"));

        ResponseEntity<Map<String, Object>> response = handler.handleInsufficientFunds(exception);

        assertEquals(HttpStatus.PAYMENT_REQUIRED, response.getStatusCode());
        Map<String, Object> body = requireBody(response);
        assertEquals("INSUFFICIENT_FUNDS", body.get("error"));
        assertEquals("ACH daily limit exceeded", body.get("message"));
        assertEquals(new BigDecimal("500.00"), body.get("requestedAmount"));
        assertEquals(new BigDecimal("120.00"), body.get("availableAmount"));
    }

    @Test
    void handleValidation_returns400WithFieldErrors() throws NoSuchMethodException {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "paymentRequest");
        bindingResult.rejectValue(null, "NotNull", "Merchant ID is required");
        bindingResult.addError(new org.springframework.validation.FieldError(
                "paymentRequest", "amount", "Amount must be at least 0.01"));
        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("annotatedMethod", String.class), 0);

        ResponseEntity<Map<String, Object>> response =
                handler.handleValidation(new MethodArgumentNotValidException(parameter, bindingResult));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = requireBody(response);
        assertEquals("VALIDATION_ERROR", body.get("error"));
        assertEquals(Map.of("amount", "Amount must be at least 0.01"), body.get("fields"));
    }

    @Test
    void handleValidation_withoutFieldErrors_returnsEmptyFieldMap() throws NoSuchMethodException {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "paymentRequest");
        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("annotatedMethod", String.class), 0);

        ResponseEntity<Map<String, Object>> response =
                handler.handleValidation(new MethodArgumentNotValidException(parameter, bindingResult));

        assertEquals(Map.of(), requireBody(response).get("fields"));
    }

    @Test
    void handleGeneral_returns500AndIncludesCause() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleGeneral(new IllegalStateException("connection pool exhausted"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        Map<String, Object> body = requireBody(response);
        assertEquals("INTERNAL_ERROR", body.get("error"));
        assertTrue(((String) body.get("message")).contains("connection pool exhausted"));
    }

    private static Map<String, Object> requireBody(ResponseEntity<Map<String, Object>> response) {
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        return body;
    }
}
