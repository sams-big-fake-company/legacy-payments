package com.bigfake.payments.util;

import com.bigfake.payments.model.dto.PaymentRequest;
import com.bigfake.payments.model.enums.PaymentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PaymentValidatorTest {

    private PaymentValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PaymentValidator();
    }

    @Test
    void validate_validRequest_noErrors() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .customerEmail("test@example.com")
                .build();

        List<String> errors = validator.validate(request);
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_nullRequest() {
        List<String> errors = validator.validate(null);
        assertEquals(1, errors.size());
        assertEquals("Payment request cannot be null", errors.get(0));
    }

    @Test
    void validate_nullMerchantId() {
        PaymentRequest request = PaymentRequest.builder()
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentType(PaymentType.WIRE)
                .build();

        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Merchant ID is required"));
    }

    @Test
    void validate_nullAmount() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .currency("USD")
                .paymentType(PaymentType.WIRE)
                .build();

        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Amount is required"));
    }

    @Test
    void validate_negativeAmount() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("-10.00"))
                .currency("USD")
                .paymentType(PaymentType.WIRE)
                .build();

        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Amount must be positive"));
    }

    @Test
    void validate_zeroAmount() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(BigDecimal.ZERO)
                .currency("USD")
                .paymentType(PaymentType.WIRE)
                .build();

        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Amount must be positive"));
    }

    @Test
    void validate_amountExceedsAbsoluteMax() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("1000001.00"))
                .currency("USD")
                .paymentType(PaymentType.WIRE)
                .build();

        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("exceeds absolute maximum")));
    }

    @Test
    void validate_nullCurrency() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .paymentType(PaymentType.WIRE)
                .build();

        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Currency is required"));
    }

    @Test
    void validate_emptyCurrency() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("")
                .paymentType(PaymentType.WIRE)
                .build();

        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Currency is required"));
    }

    @Test
    void validate_invalidCurrencyLength() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("US")
                .paymentType(PaymentType.WIRE)
                .build();

        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Currency must be a 3-letter ISO code"));
    }

    @Test
    void validate_unsupportedCurrency() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("XYZ")
                .paymentType(PaymentType.WIRE)
                .build();

        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Unsupported currency")));
    }

    @Test
    void validate_nullPaymentType() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .build();

        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Payment type is required"));
    }

    @Test
    void validate_invalidEmail() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentType(PaymentType.WIRE)
                .customerEmail("not-an-email")
                .build();

        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Invalid email format")));
    }

    @Test
    void validate_validEmail_noError() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentType(PaymentType.WIRE)
                .customerEmail("user@domain.com")
                .build();

        List<String> errors = validator.validate(request);
        assertFalse(errors.stream().anyMatch(e -> e.contains("email")));
    }

    @Test
    void validate_creditCard_missingCardLastFour() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .build();

        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Card last four digits required for card payments"));
    }

    @Test
    void validate_debit_missingCardLastFour() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentType(PaymentType.DEBIT)
                .build();

        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Card last four digits required for card payments"));
    }

    @Test
    void validate_creditCard_invalidCardLastFour() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("12AB")
                .build();

        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Card last four must be exactly 4 digits"));
    }

    @Test
    void validate_descriptionTooLong() {
        String longDescription = "x".repeat(501);
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentType(PaymentType.WIRE)
                .description(longDescription)
                .build();

        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Description must not exceed 500 characters"));
    }

    @Test
    void validate_descriptionExactly500_noError() {
        String description = "x".repeat(500);
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentType(PaymentType.WIRE)
                .description(description)
                .build();

        List<String> errors = validator.validate(request);
        assertFalse(errors.stream().anyMatch(e -> e.contains("Description")));
    }

    @Test
    void isCurrencySupported_supported() {
        assertTrue(validator.isCurrencySupported("USD"));
        assertTrue(validator.isCurrencySupported("EUR"));
    }

    @Test
    void isCurrencySupported_caseInsensitive() {
        assertTrue(validator.isCurrencySupported("usd"));
    }

    @Test
    void isCurrencySupported_notSupported() {
        assertFalse(validator.isCurrencySupported("XYZ"));
    }

    @Test
    void isCurrencySupported_null() {
        assertFalse(validator.isCurrencySupported(null));
    }

    @Test
    void isValidCardLastFour_valid() {
        assertTrue(validator.isValidCardLastFour("1234"));
        assertTrue(validator.isValidCardLastFour("0000"));
    }

    @Test
    void isValidCardLastFour_invalid() {
        assertFalse(validator.isValidCardLastFour("123"));
        assertFalse(validator.isValidCardLastFour("12345"));
        assertFalse(validator.isValidCardLastFour("abcd"));
        assertFalse(validator.isValidCardLastFour(null));
    }
}
