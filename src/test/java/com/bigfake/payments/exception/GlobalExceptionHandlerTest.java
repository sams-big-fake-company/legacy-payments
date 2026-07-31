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

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for GlobalExceptionHandler response mapping.
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @SuppressWarnings("unused")
    private void annotatedMethod(String argument) {
        // Target used only to build a MethodParameter for MethodArgumentNotValidException.
    }

    @Test
    void handlePaymentException_returns400WithErrorCode() {
        ResponseEntity<Map<String, Object>> response =
                handler.handlePaymentException(new PaymentException("Merchant is inactive", "MERCHANT_INACTIVE"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("MERCHANT_INACTIVE", body.get("error"));
        assertEquals("Merchant is inactive", body.get("message"));
        assertNotNull(body.get("timestamp"));
    }

    @Test
    void handlePaymentException_usesDefaultErrorCode() {
        ResponseEntity<Map<String, Object>> response =
                handler.handlePaymentException(new PaymentException("Something broke"));

        assertNotNull(response.getBody());
        assertEquals("PAYMENT_ERROR", response.getBody().get("error"));
    }

    @Test
    void handleInsufficientFunds_returns402WithAmounts() {
        InsufficientFundsException exception = new InsufficientFundsException(
                "Merchant daily limit would be exceeded",
                new BigDecimal("500.00"),
                new BigDecimal("120.00"));

        ResponseEntity<Map<String, Object>> response = handler.handleInsufficientFunds(exception);

        assertEquals(HttpStatus.PAYMENT_REQUIRED, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("INSUFFICIENT_FUNDS", body.get("error"));
        assertEquals("Merchant daily limit would be exceeded", body.get("message"));
        assertEquals(new BigDecimal("500.00"), body.get("requestedAmount"));
        assertEquals(new BigDecimal("120.00"), body.get("availableAmount"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleValidation_returnsFieldErrors() throws NoSuchMethodException {
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("annotatedMethod", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "paymentRequest");
        bindingResult.addError(new FieldError("paymentRequest", "amount", "Amount is required"));
        bindingResult.addError(new FieldError("paymentRequest", "currency", "Currency is required"));

        ResponseEntity<Map<String, Object>> response =
                handler.handleValidation(new MethodArgumentNotValidException(parameter, bindingResult));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("VALIDATION_ERROR", body.get("error"));
        Map<String, String> fields = (Map<String, String>) body.get("fields");
        assertEquals("Currency is required", fields.get("currency"));
    }

    @Test
    void handleGeneral_returns500() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleGeneral(new IllegalStateException("database unreachable"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("INTERNAL_ERROR", body.get("error"));
        assertTrue(((String) body.get("message")).contains("database unreachable"));
    }
}
