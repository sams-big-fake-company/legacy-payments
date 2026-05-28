package com.bigfake.payments.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

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
    void handleInsufficientFunds_returnsPaymentRequired() {
        InsufficientFundsException ex = new InsufficientFundsException(
                "Not enough",
                new BigDecimal("500.00"),
                new BigDecimal("100.00")
        );

        ResponseEntity<Map<String, Object>> response = handler.handleInsufficientFunds(ex);

        assertEquals(HttpStatus.PAYMENT_REQUIRED, response.getStatusCode());
        assertEquals("INSUFFICIENT_FUNDS", response.getBody().get("error"));
        assertEquals(new BigDecimal("500.00"), response.getBody().get("requestedAmount"));
        assertEquals(new BigDecimal("100.00"), response.getBody().get("availableAmount"));
    }

    @Test
    void handleValidation_returnsBadRequestWithFieldErrors() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError1 = new FieldError("request", "amount", "Amount is required");
        FieldError fieldError2 = new FieldError("request", "currency", "Currency is required");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError1, fieldError2));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("VALIDATION_ERROR", response.getBody().get("error"));
        @SuppressWarnings("unchecked")
        Map<String, String> fields = (Map<String, String>) response.getBody().get("fields");
        assertEquals("Amount is required", fields.get("amount"));
        assertEquals("Currency is required", fields.get("currency"));
    }

    @Test
    void handleGeneral_returnsInternalServerError() {
        RuntimeException ex = new RuntimeException("Unexpected!");

        ResponseEntity<Map<String, Object>> response = handler.handleGeneral(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getBody().get("error"));
        assertTrue(response.getBody().get("message").toString().contains("Unexpected!"));
    }
}
