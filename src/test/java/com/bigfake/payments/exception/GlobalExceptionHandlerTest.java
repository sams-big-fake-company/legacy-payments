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
        PaymentException ex = new PaymentException("Payment failed", "PAYMENT_DECLINED");

        ResponseEntity<Map<String, Object>> response = handler.handlePaymentException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("PAYMENT_DECLINED", response.getBody().get("error"));
        assertEquals("Payment failed", response.getBody().get("message"));
        assertNotNull(response.getBody().get("timestamp"));
    }

    @Test
    void handleInsufficientFunds_returnsPaymentRequired() {
        InsufficientFundsException ex = new InsufficientFundsException(
                "Limit exceeded",
                new BigDecimal("500.00"),
                new BigDecimal("300.00")
        );

        ResponseEntity<Map<String, Object>> response = handler.handleInsufficientFunds(ex);

        assertEquals(HttpStatus.PAYMENT_REQUIRED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INSUFFICIENT_FUNDS", response.getBody().get("error"));
        assertEquals(new BigDecimal("500.00"), response.getBody().get("requestedAmount"));
        assertEquals(new BigDecimal("300.00"), response.getBody().get("availableAmount"));
        assertNotNull(response.getBody().get("timestamp"));
    }

    @Test
    void handleValidation_returnsBadRequestWithFieldErrors() {
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "amount", "must be positive"));
        bindingResult.addError(new FieldError("request", "currency", "is required"));
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("VALIDATION_ERROR", response.getBody().get("error"));
        @SuppressWarnings("unchecked")
        Map<String, String> fields = (Map<String, String>) response.getBody().get("fields");
        assertNotNull(fields);
        assertEquals("must be positive", fields.get("amount"));
        assertEquals("is required", fields.get("currency"));
    }

    @Test
    void handleGeneral_returnsInternalServerError() {
        Exception ex = new RuntimeException("Something unexpected");

        ResponseEntity<Map<String, Object>> response = handler.handleGeneral(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INTERNAL_ERROR", response.getBody().get("error"));
        assertTrue(response.getBody().get("message").toString().contains("Something unexpected"));
        assertNotNull(response.getBody().get("timestamp"));
    }
}
