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
                .amount(new BigDecimal("99.99"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .customerEmail("test@example.com");
    }

    // --- validate: valid request ---

    @Test
    void validate_validRequest_returnsEmptyList() {
        List<String> errors = validator.validate(validRequestBuilder().build());
        assertTrue(errors.isEmpty());
    }

    // --- validate: null request ---

    @Test
    void validate_nullRequest_returnsError() {
        List<String> errors = validator.validate(null);
        assertEquals(1, errors.size());
        assertEquals("Payment request cannot be null", errors.get(0));
    }

    // --- validate: merchantId ---

    @Test
    void validate_nullMerchantId_returnsError() {
        PaymentRequest request = validRequestBuilder().merchantId(null).build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Merchant ID is required"));
    }

    // --- validate: amount ---

    @Test
    void validate_nullAmount_returnsError() {
        PaymentRequest request = validRequestBuilder().amount(null).build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Amount is required"));
    }

    @Test
    void validate_zeroAmount_returnsError() {
        PaymentRequest request = validRequestBuilder().amount(BigDecimal.ZERO).build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Amount must be positive"));
    }

    @Test
    void validate_negativeAmount_returnsError() {
        PaymentRequest request = validRequestBuilder().amount(new BigDecimal("-10.00")).build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Amount must be positive"));
    }

    @Test
    void validate_amountExceedsMax_returnsError() {
        PaymentRequest request = validRequestBuilder().amount(new BigDecimal("1000001.00")).build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("exceeds absolute maximum")));
    }

    @Test
    void validate_amountAtMaxBoundary_noError() {
        PaymentRequest request = validRequestBuilder().amount(new BigDecimal("1000000.00")).build();
        List<String> errors = validator.validate(request);
        assertFalse(errors.stream().anyMatch(e -> e.contains("exceeds absolute maximum")));
    }

    @Test
    void validate_amountWithMoreThanTwoDecimals_noError() {
        PaymentRequest request = validRequestBuilder().amount(new BigDecimal("99.999")).build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.isEmpty());
    }

    // --- validate: currency ---

    @Test
    void validate_nullCurrency_returnsError() {
        PaymentRequest request = validRequestBuilder().currency(null).build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Currency is required"));
    }

    @Test
    void validate_emptyCurrency_returnsError() {
        PaymentRequest request = validRequestBuilder().currency("").build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Currency is required"));
    }

    @Test
    void validate_invalidLengthCurrency_returnsError() {
        PaymentRequest request = validRequestBuilder().currency("US").build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Currency must be a 3-letter ISO code"));
    }

    @Test
    void validate_unsupportedCurrency_returnsError() {
        PaymentRequest request = validRequestBuilder().currency("XYZ").build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Unsupported currency")));
    }

    // --- validate: paymentType ---

    @Test
    void validate_nullPaymentType_returnsError() {
        PaymentRequest request = validRequestBuilder().paymentType(null).build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Payment type is required"));
    }

    // --- validate: email ---

    @Test
    void validate_invalidEmail_returnsError() {
        PaymentRequest request = validRequestBuilder().customerEmail("not-an-email").build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Invalid email format")));
    }

    @Test
    void validate_validEmail_noError() {
        PaymentRequest request = validRequestBuilder().customerEmail("user@domain.com").build();
        List<String> errors = validator.validate(request);
        assertFalse(errors.stream().anyMatch(e -> e.contains("email")));
    }

    @Test
    void validate_nullEmail_noError() {
        PaymentRequest request = validRequestBuilder().customerEmail(null).build();
        List<String> errors = validator.validate(request);
        assertFalse(errors.stream().anyMatch(e -> e.contains("email")));
    }

    @Test
    void validate_emptyEmail_noError() {
        PaymentRequest request = validRequestBuilder().customerEmail("").build();
        List<String> errors = validator.validate(request);
        assertFalse(errors.stream().anyMatch(e -> e.contains("email")));
    }

    // --- validate: card info for card payment types ---

    @Test
    void validate_creditCardMissingCardLastFour_returnsError() {
        PaymentRequest request = validRequestBuilder()
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour(null)
                .build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Card last four")));
    }

    @Test
    void validate_debitMissingCardLastFour_returnsError() {
        PaymentRequest request = validRequestBuilder()
                .paymentType(PaymentType.DEBIT)
                .cardLastFour(null)
                .build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Card last four")));
    }

    @Test
    void validate_cardLastFourInvalidFormat_returnsError() {
        PaymentRequest request = validRequestBuilder()
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("abcd")
                .build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("4 digits")));
    }

    @Test
    void validate_wireTransferNoCardRequired() {
        PaymentRequest request = validRequestBuilder()
                .paymentType(PaymentType.WIRE)
                .cardLastFour(null)
                .build();
        List<String> errors = validator.validate(request);
        assertFalse(errors.stream().anyMatch(e -> e.contains("Card last four")));
    }

    @Test
    void validate_achNoCardRequired() {
        PaymentRequest request = validRequestBuilder()
                .paymentType(PaymentType.ACH)
                .cardLastFour(null)
                .build();
        List<String> errors = validator.validate(request);
        assertFalse(errors.stream().anyMatch(e -> e.contains("Card last four")));
    }

    @Test
    void validate_emptyCardLastFour_returnsError() {
        PaymentRequest request = validRequestBuilder()
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("")
                .build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Card last four")));
    }

    // --- validate: description ---

    @Test
    void validate_descriptionTooLong_returnsError() {
        String longDescription = "a".repeat(501);
        PaymentRequest request = validRequestBuilder().description(longDescription).build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Description must not exceed 500")));
    }

    @Test
    void validate_descriptionAtLimit_noError() {
        String description = "a".repeat(500);
        PaymentRequest request = validRequestBuilder().description(description).build();
        List<String> errors = validator.validate(request);
        assertFalse(errors.stream().anyMatch(e -> e.contains("Description")));
    }

    // --- validate: multiple errors ---

    @Test
    void validate_multipleErrors_returnsAllErrors() {
        PaymentRequest request = PaymentRequest.builder()
                .build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.size() >= 3);
    }

    // --- isCurrencySupported ---

    @Test
    void isCurrencySupported_validCurrency_returnsTrue() {
        assertTrue(validator.isCurrencySupported("USD"));
        assertTrue(validator.isCurrencySupported("EUR"));
        assertTrue(validator.isCurrencySupported("GBP"));
    }

    @Test
    void isCurrencySupported_lowercaseValid_returnsTrue() {
        assertTrue(validator.isCurrencySupported("usd"));
    }

    @Test
    void isCurrencySupported_invalidCurrency_returnsFalse() {
        assertFalse(validator.isCurrencySupported("XYZ"));
    }

    @Test
    void isCurrencySupported_null_returnsFalse() {
        assertFalse(validator.isCurrencySupported(null));
    }

    // --- isValidCardLastFour ---

    @Test
    void isValidCardLastFour_validDigits_returnsTrue() {
        assertTrue(validator.isValidCardLastFour("4242"));
        assertTrue(validator.isValidCardLastFour("0000"));
        assertTrue(validator.isValidCardLastFour("9999"));
    }

    @Test
    void isValidCardLastFour_letters_returnsFalse() {
        assertFalse(validator.isValidCardLastFour("abcd"));
    }

    @Test
    void isValidCardLastFour_tooShort_returnsFalse() {
        assertFalse(validator.isValidCardLastFour("123"));
    }

    @Test
    void isValidCardLastFour_tooLong_returnsFalse() {
        assertFalse(validator.isValidCardLastFour("12345"));
    }

    @Test
    void isValidCardLastFour_null_returnsFalse() {
        assertFalse(validator.isValidCardLastFour(null));
    }

    @Test
    void isValidCardLastFour_mixed_returnsFalse() {
        assertFalse(validator.isValidCardLastFour("12ab"));
    }
}
