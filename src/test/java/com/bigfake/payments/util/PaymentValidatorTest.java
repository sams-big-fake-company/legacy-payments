package com.bigfake.payments.util;

import com.bigfake.payments.model.dto.PaymentRequest;
import com.bigfake.payments.model.enums.PaymentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PaymentValidator.
 */
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

    @Test
    void validate_validRequest_returnsNoErrors() {
        List<String> errors = validator.validate(validRequestBuilder().build());
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_nullRequest_returnsSingleError() {
        List<String> errors = validator.validate(null);
        assertEquals(List.of("Payment request cannot be null"), errors);
    }

    @Test
    void validate_missingMerchantId_returnsError() {
        List<String> errors = validator.validate(validRequestBuilder().merchantId(null).build());
        assertTrue(errors.contains("Merchant ID is required"));
    }

    @Test
    void validate_missingAmount_returnsError() {
        List<String> errors = validator.validate(validRequestBuilder().amount(null).build());
        assertTrue(errors.contains("Amount is required"));
    }

    @Test
    void validate_zeroAmount_returnsError() {
        List<String> errors = validator.validate(validRequestBuilder().amount(BigDecimal.ZERO).build());
        assertTrue(errors.contains("Amount must be positive"));
    }

    @Test
    void validate_negativeAmount_returnsError() {
        List<String> errors = validator.validate(validRequestBuilder().amount(new BigDecimal("-5.00")).build());
        assertTrue(errors.contains("Amount must be positive"));
    }

    @Test
    void validate_amountAboveAbsoluteMax_returnsError() {
        List<String> errors = validator.validate(
                validRequestBuilder().amount(new BigDecimal("1000000.01")).build());
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).startsWith("Amount exceeds absolute maximum"));
    }

    @Test
    void validate_amountAtAbsoluteMax_isAllowed() {
        List<String> errors = validator.validate(
                validRequestBuilder().amount(new BigDecimal("1000000.00")).build());
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_amountWithMoreThanTwoDecimals_isAllowedButLogged() {
        List<String> errors = validator.validate(
                validRequestBuilder().amount(new BigDecimal("10.1234")).build());
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_missingCurrency_returnsError() {
        List<String> errors = validator.validate(validRequestBuilder().currency(null).build());
        assertTrue(errors.contains("Currency is required"));
    }

    @Test
    void validate_emptyCurrency_returnsError() {
        List<String> errors = validator.validate(validRequestBuilder().currency("").build());
        assertTrue(errors.contains("Currency is required"));
    }

    @Test
    void validate_nonThreeLetterCurrency_returnsTwoErrors() {
        List<String> errors = validator.validate(validRequestBuilder().currency("USDD").build());
        assertTrue(errors.contains("Currency must be a 3-letter ISO code"));
        assertTrue(errors.contains("Unsupported currency: USDD"));
    }

    @Test
    void validate_unsupportedCurrency_returnsError() {
        List<String> errors = validator.validate(validRequestBuilder().currency("ZWL").build());
        assertEquals(List.of("Unsupported currency: ZWL"), errors);
    }

    @Test
    void validate_lowercaseCurrency_isAccepted() {
        List<String> errors = validator.validate(validRequestBuilder().currency("usd").build());
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_missingPaymentType_returnsError() {
        List<String> errors = validator.validate(
                validRequestBuilder().paymentType(null).cardLastFour(null).build());
        assertEquals(List.of("Payment type is required"), errors);
    }

    @Test
    void validate_invalidEmail_returnsError() {
        List<String> errors = validator.validate(
                validRequestBuilder().customerEmail("not-an-email").build());
        assertEquals(List.of("Invalid email format: not-an-email"), errors);
    }

    @Test
    void validate_nullEmail_isAllowed() {
        List<String> errors = validator.validate(validRequestBuilder().customerEmail(null).build());
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_emptyEmail_isAllowed() {
        List<String> errors = validator.validate(validRequestBuilder().customerEmail("").build());
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_creditCardMissingLastFour_returnsError() {
        List<String> errors = validator.validate(validRequestBuilder().cardLastFour(null).build());
        assertEquals(List.of("Card last four digits required for card payments"), errors);
    }

    @Test
    void validate_debitMissingLastFour_returnsError() {
        List<String> errors = validator.validate(
                validRequestBuilder().paymentType(PaymentType.DEBIT).cardLastFour("").build());
        assertEquals(List.of("Card last four digits required for card payments"), errors);
    }

    @Test
    void validate_cardLastFourNotDigits_returnsError() {
        List<String> errors = validator.validate(validRequestBuilder().cardLastFour("42ab").build());
        assertEquals(List.of("Card last four must be exactly 4 digits"), errors);
    }

    @Test
    void validate_wirePayment_doesNotRequireCardInfo() {
        List<String> errors = validator.validate(
                validRequestBuilder().paymentType(PaymentType.WIRE).cardLastFour(null).build());
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_descriptionTooLong_returnsError() {
        String longDescription = "a".repeat(501);
        List<String> errors = validator.validate(
                validRequestBuilder().description(longDescription).build());
        assertEquals(List.of("Description must not exceed 500 characters"), errors);
    }

    @Test
    void validate_descriptionAtMaxLength_isAllowed() {
        String description = "a".repeat(500);
        List<String> errors = validator.validate(
                validRequestBuilder().description(description).build());
        assertTrue(errors.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"USD", "EUR", "GBP", "CAD", "AUD", "JPY", "CHF", "CNY", "INR", "BRL"})
    void isCurrencySupported_supportedCurrencies_returnsTrue(String currency) {
        assertTrue(validator.isCurrencySupported(currency));
    }

    @Test
    void isCurrencySupported_lowercase_returnsTrue() {
        assertTrue(validator.isCurrencySupported("eur"));
    }

    @Test
    void isCurrencySupported_unsupported_returnsFalse() {
        assertFalse(validator.isCurrencySupported("ZWL"));
    }

    @Test
    void isCurrencySupported_null_returnsFalse() {
        assertFalse(validator.isCurrencySupported(null));
    }

    @Test
    void isValidCardLastFour_validDigits_returnsTrue() {
        assertTrue(validator.isValidCardLastFour("1234"));
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
    void isValidCardLastFour_nonDigits_returnsFalse() {
        assertFalse(validator.isValidCardLastFour("12a4"));
    }

    @Test
    void isValidCardLastFour_null_returnsFalse() {
        assertFalse(validator.isValidCardLastFour(null));
    }
}
