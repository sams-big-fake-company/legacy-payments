package com.bigfake.payments.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handlePaymentException_returnsBadRequest() {
        PaymentException ex = new PaymentException("Payment failed", "PAYMENT_FAILED");

        ResponseEntity<Map<String, Object>> response = handler.handlePaymentException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("PAYMENT_FAILED", response.getBody().get("error"));
        assertEquals("Payment failed", response.getBody().get("message"));
        assertNotNull(response.getBody().get("timestamp"));
    }

    @Test
    void handlePaymentException_defaultErrorCode() {
        PaymentException ex = new PaymentException("Something went wrong");

        ResponseEntity<Map<String, Object>> response = handler.handlePaymentException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("PAYMENT_ERROR", response.getBody().get("error"));
    }

    @Test
    void handleInsufficientFunds_returnsPaymentRequired() {
        InsufficientFundsException ex = new InsufficientFundsException(
                "Not enough funds",
                new BigDecimal("500.00"),
                new BigDecimal("200.00")
        );

        ResponseEntity<Map<String, Object>> response = handler.handleInsufficientFunds(ex);

        assertEquals(HttpStatus.PAYMENT_REQUIRED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INSUFFICIENT_FUNDS", response.getBody().get("error"));
        assertEquals("Not enough funds", response.getBody().get("message"));
        assertEquals(new BigDecimal("500.00"), response.getBody().get("requestedAmount"));
        assertEquals(new BigDecimal("200.00"), response.getBody().get("availableAmount"));
        assertNotNull(response.getBody().get("timestamp"));
    }

    @Test
    void handleValidation_returnsBadRequest() {
        BindingResult bindingResult = mock(BindingResult.class);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        FieldError fieldError1 = new FieldError("request", "amount", "Amount is required");
        FieldError fieldError2 = new FieldError("request", "currency", "Currency is required");
        when(bindingResult.getFieldErrors()).thenReturn(Arrays.asList(fieldError1, fieldError2));

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("VALIDATION_ERROR", response.getBody().get("error"));

        @SuppressWarnings("unchecked")
        Map<String, String> fields = (Map<String, String>) response.getBody().get("fields");
        assertNotNull(fields);
        assertEquals("Amount is required", fields.get("amount"));
        assertEquals("Currency is required", fields.get("currency"));
    }

    @Test
    void handleGeneral_returnsInternalServerError() {
        Exception ex = new RuntimeException("Unexpected error");

        ResponseEntity<Map<String, Object>> response = handler.handleGeneral(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INTERNAL_ERROR", response.getBody().get("error"));
        assertTrue(response.getBody().get("message").toString().contains("Unexpected error"));
        assertNotNull(response.getBody().get("timestamp"));
    }

    @Test
    void handleGeneral_nullPointerException() {
        Exception ex = new NullPointerException("null ref");

        ResponseEntity<Map<String, Object>> response = handler.handleGeneral(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getBody().get("error"));
    }

    @Test
    void handlePaymentException_withCause() {
        PaymentException ex = new PaymentException("Gateway error", new RuntimeException("Connection timeout"));

        ResponseEntity<Map<String, Object>> response = handler.handlePaymentException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("PAYMENT_ERROR", response.getBody().get("error"));
        assertEquals("Gateway error", response.getBody().get("message"));
    }
}
