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

    private PaymentRequest.PaymentRequestBuilder validRequestBuilder() {
        return PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .customerEmail("test@example.com");
    }

    @Test
    void validate_validRequest_returnsNoErrors() {
        List<String> errors = validator.validate(validRequestBuilder().build());
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_nullRequest_returnsError() {
        List<String> errors = validator.validate(null);
        assertEquals(1, errors.size());
        assertEquals("Payment request cannot be null", errors.get(0));
    }

    @Test
    void validate_nullMerchantId_returnsError() {
        PaymentRequest request = validRequestBuilder().merchantId(null).build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Merchant ID is required")));
    }

    @Test
    void validate_nullAmount_returnsError() {
        PaymentRequest request = validRequestBuilder().amount(null).build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Amount is required")));
    }

    @Test
    void validate_zeroAmount_returnsError() {
        PaymentRequest request = validRequestBuilder().amount(BigDecimal.ZERO).build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Amount must be positive")));
    }

    @Test
    void validate_negativeAmount_returnsError() {
        PaymentRequest request = validRequestBuilder().amount(new BigDecimal("-10.00")).build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Amount must be positive")));
    }

    @Test
    void validate_amountExceedsAbsoluteMax_returnsError() {
        PaymentRequest request = validRequestBuilder().amount(new BigDecimal("1000001.00")).build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("exceeds absolute maximum")));
    }

    @Test
    void validate_amountWithMoreThanTwoDecimals_noError() {
        PaymentRequest request = validRequestBuilder().amount(new BigDecimal("50.123")).build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_nullCurrency_returnsError() {
        PaymentRequest request = validRequestBuilder().currency(null).build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Currency is required")));
    }

    @Test
    void validate_emptyCurrency_returnsError() {
        PaymentRequest request = validRequestBuilder().currency("").build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Currency is required")));
    }

    @Test
    void validate_invalidCurrencyLength_returnsError() {
        PaymentRequest request = validRequestBuilder().currency("US").build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("3-letter ISO code")));
    }

    @Test
    void validate_unsupportedCurrency_returnsError() {
        PaymentRequest request = validRequestBuilder().currency("XYZ").build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Unsupported currency")));
    }

    @Test
    void validate_nullPaymentType_returnsError() {
        PaymentRequest request = validRequestBuilder().paymentType(null).build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Payment type is required")));
    }

    @Test
    void validate_invalidEmail_returnsError() {
        PaymentRequest request = validRequestBuilder().customerEmail("not-an-email").build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Invalid email format")));
    }

    @Test
    void validate_validEmail_noError() {
        PaymentRequest request = validRequestBuilder().customerEmail("user@example.com").build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_nullEmail_noError() {
        PaymentRequest request = validRequestBuilder().customerEmail(null).build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_emptyEmail_noError() {
        PaymentRequest request = validRequestBuilder().customerEmail("").build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_creditCard_missingCardLastFour_returnsError() {
        PaymentRequest request = validRequestBuilder()
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour(null)
                .build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Card last four digits required")));
    }

    @Test
    void validate_creditCard_emptyCardLastFour_returnsError() {
        PaymentRequest request = validRequestBuilder()
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("")
                .build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Card last four digits required")));
    }

    @Test
    void validate_creditCard_invalidCardLastFour_returnsError() {
        PaymentRequest request = validRequestBuilder()
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("12ab")
                .build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("4 digits")));
    }

    @Test
    void validate_creditCard_tooFewDigits_returnsError() {
        PaymentRequest request = validRequestBuilder()
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("123")
                .build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("4 digits")));
    }

    @Test
    void validate_debit_missingCardLastFour_returnsError() {
        PaymentRequest request = validRequestBuilder()
                .paymentType(PaymentType.DEBIT)
                .cardLastFour(null)
                .build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Card last four digits required")));
    }

    @Test
    void validate_wire_noCardRequired() {
        PaymentRequest request = validRequestBuilder()
                .paymentType(PaymentType.WIRE)
                .cardLastFour(null)
                .build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_ach_noCardRequired() {
        PaymentRequest request = validRequestBuilder()
                .paymentType(PaymentType.ACH)
                .cardLastFour(null)
                .build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_descriptionTooLong_returnsError() {
        String longDescription = "x".repeat(501);
        PaymentRequest request = validRequestBuilder().description(longDescription).build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Description must not exceed 500")));
    }

    @Test
    void validate_descriptionExactly500_noError() {
        String description = "x".repeat(500);
        PaymentRequest request = validRequestBuilder().description(description).build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_multipleErrors_returnsAll() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(null)
                .amount(null)
                .currency(null)
                .paymentType(null)
                .build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.size() >= 3);
    }

    // --- isCurrencySupported ---

    @Test
    void isCurrencySupported_supported_returnsTrue() {
        assertTrue(validator.isCurrencySupported("USD"));
        assertTrue(validator.isCurrencySupported("EUR"));
    }

    @Test
    void isCurrencySupported_unsupported_returnsFalse() {
        assertFalse(validator.isCurrencySupported("XYZ"));
    }

    @Test
    void isCurrencySupported_null_returnsFalse() {
        assertFalse(validator.isCurrencySupported(null));
    }

    @Test
    void isCurrencySupported_caseInsensitive() {
        assertTrue(validator.isCurrencySupported("usd"));
    }

    // --- isValidCardLastFour ---

    @Test
    void isValidCardLastFour_valid_returnsTrue() {
        assertTrue(validator.isValidCardLastFour("1234"));
        assertTrue(validator.isValidCardLastFour("0000"));
    }

    @Test
    void isValidCardLastFour_null_returnsFalse() {
        assertFalse(validator.isValidCardLastFour(null));
    }

    @Test
    void isValidCardLastFour_invalid_returnsFalse() {
        assertFalse(validator.isValidCardLastFour("12ab"));
        assertFalse(validator.isValidCardLastFour("123"));
        assertFalse(validator.isValidCardLastFour("12345"));
    }
}
