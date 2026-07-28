package com.bigfake.payments.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the error payloads produced by {@link GlobalExceptionHandler}.
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Mock
    private MethodArgumentNotValidException validationException;

    @Mock
    private BindingResult bindingResult;

    @Test
    void paymentExceptionsBecomeBadRequestWithTheErrorCode() {
        ResponseEntity<Map<String, Object>> response =
                handler.handlePaymentException(new PaymentException("Merchant not found: 7", "MERCHANT_NOT_FOUND"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("MERCHANT_NOT_FOUND", response.getBody().get("error"));
        assertEquals("Merchant not found: 7", response.getBody().get("message"));
        assertNotNull(response.getBody().get("timestamp"));
    }

    @Test
    void insufficientFundsBecomesPaymentRequiredWithTheAmounts() {
        InsufficientFundsException exception = new InsufficientFundsException(
                "Merchant daily limit would be exceeded",
                new BigDecimal("100.00"),
                new BigDecimal("50.00"));

        ResponseEntity<Map<String, Object>> response = handler.handleInsufficientFunds(exception);

        assertEquals(HttpStatus.PAYMENT_REQUIRED, response.getStatusCode());
        assertEquals("INSUFFICIENT_FUNDS", response.getBody().get("error"));
        assertEquals(new BigDecimal("100.00"), response.getBody().get("requestedAmount"));
        assertEquals(new BigDecimal("50.00"), response.getBody().get("availableAmount"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void validationErrorsAreReportedPerField() {
        when(validationException.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(Arrays.asList(
                new FieldError("paymentRequest", "amount", "Amount must be at least 0.01"),
                new FieldError("paymentRequest", "currency", "Currency is required")));

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(validationException);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("VALIDATION_ERROR", response.getBody().get("error"));
        Map<String, String> fields = (Map<String, String>) response.getBody().get("fields");
        assertEquals("Amount must be at least 0.01", fields.get("amount"));
        assertEquals("Currency is required", fields.get("currency"));
    }

    @Test
    void unexpectedExceptionsBecomeInternalServerErrors() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleGeneral(new IllegalStateException("connection pool exhausted"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getBody().get("error"));
        assertEquals("An unexpected error occurred: connection pool exhausted",
                response.getBody().get("message"));
    }
}
