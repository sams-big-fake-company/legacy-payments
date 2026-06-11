package com.bigfake.payments.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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
    void handlePaymentException_returnsBadRequestWithErrorCode() {
        PaymentException ex = new PaymentException("merchant missing", "MERCHANT_NOT_FOUND");

        ResponseEntity<Map<String, Object>> response = handler.handlePaymentException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("MERCHANT_NOT_FOUND", response.getBody().get("error"));
        assertEquals("merchant missing", response.getBody().get("message"));
        assertNotNull(response.getBody().get("timestamp"));
    }

    @Test
    void handleInsufficientFunds_returnsPaymentRequiredWithAmounts() {
        InsufficientFundsException ex = new InsufficientFundsException(
                "daily limit", new BigDecimal("500.00"), new BigDecimal("100.00"));

        ResponseEntity<Map<String, Object>> response = handler.handleInsufficientFunds(ex);

        assertEquals(HttpStatus.PAYMENT_REQUIRED, response.getStatusCode());
        assertEquals("INSUFFICIENT_FUNDS", response.getBody().get("error"));
        assertEquals(new BigDecimal("500.00"), response.getBody().get("requestedAmount"));
        assertEquals(new BigDecimal("100.00"), response.getBody().get("availableAmount"));
    }

    @SuppressWarnings("unused")
    private void dummyHandlerMethod(Object request) {
    }

    @Test
    void handleValidation_returnsFieldErrors() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Object(), "paymentRequest");
        bindingResult.addError(new FieldError("paymentRequest", "amount", "Amount is required"));
        bindingResult.addError(new FieldError("paymentRequest", "currency", "Currency is required"));
        Method method = getClass().getDeclaredMethod("dummyHandlerMethod", Object.class);
        MethodArgumentNotValidException validationException =
                new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(validationException);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("VALIDATION_ERROR", response.getBody().get("error"));
        @SuppressWarnings("unchecked")
        Map<String, String> fields = (Map<String, String>) response.getBody().get("fields");
        assertEquals("Amount is required", fields.get("amount"));
        assertEquals("Currency is required", fields.get("currency"));
    }

    @Test
    void handleGeneral_returnsInternalServerError() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleGeneral(new IllegalStateException("boom"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getBody().get("error"));
        assertEquals("An unexpected error occurred: boom", response.getBody().get("message"));
    }
}
