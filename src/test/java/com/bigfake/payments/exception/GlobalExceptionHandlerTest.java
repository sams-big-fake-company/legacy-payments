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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
    void paymentExceptionIsMappedTo400WithErrorCode() {
        ResponseEntity<Map<String, Object>> response =
                handler.handlePaymentException(new PaymentException("Merchant not found: 9", "MERCHANT_NOT_FOUND"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("MERCHANT_NOT_FOUND", response.getBody().get("error"));
        assertEquals("Merchant not found: 9", response.getBody().get("message"));
        assertNotNull(response.getBody().get("timestamp"));
    }

    @Test
    void insufficientFundsIsMappedTo402WithAmounts() {
        InsufficientFundsException exception = new InsufficientFundsException(
                "ACH daily limit exceeded", new BigDecimal("2000.00"), new BigDecimal("1000.00"));

        ResponseEntity<Map<String, Object>> response = handler.handleInsufficientFunds(exception);

        assertEquals(HttpStatus.PAYMENT_REQUIRED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INSUFFICIENT_FUNDS", response.getBody().get("error"));
        assertEquals(new BigDecimal("2000.00"), response.getBody().get("requestedAmount"));
        assertEquals(new BigDecimal("1000.00"), response.getBody().get("availableAmount"));
    }

    @Test
    void validationErrorsAreFlattenedPerField() throws NoSuchMethodException {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "paymentRequest");
        bindingResult.addError(new FieldError("paymentRequest", "amount", "Amount is required"));
        bindingResult.addError(new FieldError("paymentRequest", "currency", "Currency is required"));
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
                new org.springframework.core.MethodParameter(
                        GlobalExceptionHandlerTest.class.getDeclaredMethod("validationErrorsAreFlattenedPerField"), -1),
                bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("VALIDATION_ERROR", response.getBody().get("error"));
        assertEquals(Map.of("amount", "Amount is required", "currency", "Currency is required"),
                response.getBody().get("fields"));
    }

    @Test
    void unexpectedExceptionsAreMappedTo500() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleGeneral(new IllegalStateException("connection reset"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INTERNAL_ERROR", response.getBody().get("error"));
        assertEquals("An unexpected error occurred: connection reset", response.getBody().get("message"));
    }
}
