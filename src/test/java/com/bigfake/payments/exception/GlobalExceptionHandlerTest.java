package com.bigfake.payments.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
    void paymentExceptionsBecomeBadRequestsCarryingTheErrorCode() {
        ResponseEntity<Map<String, Object>> response =
                handler.handlePaymentException(new PaymentException("Merchant not found: 9", "MERCHANT_NOT_FOUND"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = requireBody(response);
        assertEquals("MERCHANT_NOT_FOUND", body.get("error"));
        assertEquals("Merchant not found: 9", body.get("message"));
        assertNotNull(body.get("timestamp"));
    }

    @Test
    void insufficientFundsBecomesPaymentRequiredAndExposesTheAmounts() {
        InsufficientFundsException exception = new InsufficientFundsException(
                "ACH daily limit exceeded", new BigDecimal("100.00"), new BigDecimal("50.00"));

        ResponseEntity<Map<String, Object>> response = handler.handleInsufficientFunds(exception);

        assertEquals(HttpStatus.PAYMENT_REQUIRED, response.getStatusCode());
        Map<String, Object> body = requireBody(response);
        assertEquals("INSUFFICIENT_FUNDS", body.get("error"));
        assertEquals("ACH daily limit exceeded", body.get("message"));
        assertEquals(new BigDecimal("100.00"), body.get("requestedAmount"));
        assertEquals(new BigDecimal("50.00"), body.get("availableAmount"));
    }

    @Test
    void validationErrorsAreReportedPerField() throws NoSuchMethodException {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "paymentRequest");
        bindingResult.addError(new org.springframework.validation.FieldError(
                "paymentRequest", "amount", "Amount must be at least 0.01"));
        bindingResult.addError(new org.springframework.validation.FieldError(
                "paymentRequest", "currency", "Currency is required"));
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
                new org.springframework.core.MethodParameter(
                        GlobalExceptionHandlerTest.class.getDeclaredMethod("methodWithParameter", String.class), 0),
                bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = requireBody(response);
        assertEquals("VALIDATION_ERROR", body.get("error"));
        assertEquals(Map.of(
                        "amount", "Amount must be at least 0.01",
                        "currency", "Currency is required"),
                body.get("fields"));
    }

    @Test
    void validationErrorsWithoutFieldErrorsProduceAnEmptyFieldMap() throws NoSuchMethodException {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "paymentRequest");
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
                new org.springframework.core.MethodParameter(
                        GlobalExceptionHandlerTest.class.getDeclaredMethod("methodWithParameter", String.class), 0),
                bindingResult);

        Map<String, Object> body = requireBody(handler.handleValidation(exception));

        assertEquals(Map.of(), body.get("fields"));
    }

    @Test
    void unexpectedExceptionsBecomeInternalServerErrors() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleGeneral(new IllegalStateException("connection pool exhausted"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        Map<String, Object> body = requireBody(response);
        assertEquals("INTERNAL_ERROR", body.get("error"));
        assertEquals("An unexpected error occurred: connection pool exhausted", body.get("message"));
    }

    @Test
    void everyHandlerStampsAnIsoTimestamp() {
        Map<String, Object> body = requireBody(handler.handlePaymentException(new PaymentException("boom")));

        assertTrue(String.valueOf(body.get("timestamp")).contains("T"),
                "timestamp should be an ISO-8601 local date time");
    }

    @Test
    void paymentExceptionDefaultsToAGenericErrorCode() {
        assertEquals("PAYMENT_ERROR", new PaymentException("boom").getErrorCode());
        assertEquals("PAYMENT_ERROR", new PaymentException("boom", new RuntimeException("cause")).getErrorCode());
        assertEquals("CUSTOM_CODE", new PaymentException("boom", "CUSTOM_CODE").getErrorCode());
    }

    @Test
    void paymentExceptionKeepsMessageAndCause() {
        RuntimeException cause = new RuntimeException("root cause");
        PaymentException exception = new PaymentException("wrapped", cause);

        assertEquals("wrapped", exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void insufficientFundsIsAPaymentExceptionThatKeepsTheAmounts() {
        InsufficientFundsException exception = new InsufficientFundsException(
                "limit exceeded", new BigDecimal("10.00"), new BigDecimal("2.50"));

        assertInstanceOf(PaymentException.class, exception);
        assertEquals("INSUFFICIENT_FUNDS", exception.getErrorCode());
        assertEquals(new BigDecimal("10.00"), exception.getRequestedAmount());
        assertEquals(new BigDecimal("2.50"), exception.getAvailableAmount());
    }

    private static Map<String, Object> requireBody(ResponseEntity<Map<String, Object>> response) {
        Map<String, Object> body = response.getBody();
        assertNotNull(body, "response body");
        return body;
    }

    @SuppressWarnings("unused")
    private void methodWithParameter(String parameter) {
        // Only used to build a MethodParameter for MethodArgumentNotValidException.
    }
}
