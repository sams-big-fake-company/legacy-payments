package com.bigfake.payments.exception;

import com.bigfake.payments.model.dto.PaymentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
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
    void paymentExceptionMapsToBadRequestWithErrorCode() {
        ResponseEntity<Map<String, Object>> response =
                handler.handlePaymentException(new PaymentException("Merchant not found: 7", "MERCHANT_NOT_FOUND"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("MERCHANT_NOT_FOUND", body.get("error"));
        assertEquals("Merchant not found: 7", body.get("message"));
        assertNotNull(body.get("timestamp"));
    }

    @Test
    void insufficientFundsMapsToPaymentRequiredWithAmounts() {
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

    /** Stand-in controller method used to build a MethodParameter for the exception under test. */
    @SuppressWarnings("unused")
    private void handlerMethod(PaymentRequest request) {
    }

    private MethodArgumentNotValidException validationException(FieldError... fieldErrors) throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new PaymentRequest(), "paymentRequest");
        for (FieldError fieldError : fieldErrors) {
            bindingResult.addError(fieldError);
        }
        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("handlerMethod", PaymentRequest.class), 0);
        return new MethodArgumentNotValidException(parameter, bindingResult);
    }

    @Test
    void validationErrorsAreFlattenedIntoFieldMap() throws NoSuchMethodException {
        MethodArgumentNotValidException exception = validationException(
                new FieldError("paymentRequest", "amount", "Amount must be at least 0.01"),
                new FieldError("paymentRequest", "currency", "Currency is required"));

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("VALIDATION_ERROR", body.get("error"));
        assertEquals(Map.of(
                "amount", "Amount must be at least 0.01",
                "currency", "Currency is required"), body.get("fields"));
    }

    @Test
    void validationErrorsWithNoFieldErrorsProduceEmptyFieldMap() throws NoSuchMethodException {
        ResponseEntity<Map<String, Object>> response = handler.handleValidation(validationException());

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(Map.of(), body.get("fields"));
    }

    @Test
    void unexpectedExceptionMapsToInternalServerError() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleGeneral(new IllegalStateException("database offline"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("INTERNAL_ERROR", body.get("error"));
        assertTrue(String.valueOf(body.get("message")).contains("database offline"));
    }
}
