package com.bigfake.payments.util;

import com.bigfake.payments.model.dto.PaymentRequest;
import com.bigfake.payments.model.enums.PaymentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .customerEmail("customer@example.com");
    }

    @Test
    void validate_validRequest_returnsNoErrors() {
        List<String> errors = validator.validate(validRequestBuilder().build());
        assertEquals(List.of(), errors);
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
        assertTrue(errors.contains("Amount exceeds absolute maximum of 1000000.00"));
    }

    @Test
    void validate_amountAtAbsoluteMax_isAllowed() {
        List<String> errors = validator.validate(
                validRequestBuilder().amount(new BigDecimal("1000000.00")).build());
        assertEquals(List.of(), errors);
    }

    @Test
    void validate_amountWithMoreThanTwoDecimals_isAllowedButLogged() {
        List<String> errors = validator.validate(
                validRequestBuilder().amount(new BigDecimal("10.123")).build());
        assertEquals(List.of(), errors);
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
    void validate_currencyWrongLength_returnsError() {
        List<String> errors = validator.validate(validRequestBuilder().currency("USDT").build());
        assertTrue(errors.contains("Currency must be a 3-letter ISO code"));
    }

    @Test
    void validate_unsupportedCurrency_returnsError() {
        List<String> errors = validator.validate(validRequestBuilder().currency("ZWL").build());
        assertTrue(errors.contains("Unsupported currency: ZWL"));
    }

    @Test
    void validate_lowercaseSupportedCurrency_isAccepted() {
        List<String> errors = validator.validate(validRequestBuilder().currency("eur").build());
        assertEquals(List.of(), errors);
    }

    @Test
    void validate_missingPaymentType_returnsError() {
        List<String> errors = validator.validate(
                validRequestBuilder().paymentType(null).cardLastFour(null).build());
        assertTrue(errors.contains("Payment type is required"));
    }

    @Test
    void validate_invalidEmail_returnsError() {
        List<String> errors = validator.validate(
                validRequestBuilder().customerEmail("not-an-email").build());
        assertTrue(errors.contains("Invalid email format: not-an-email"));
    }

    @Test
    void validate_emptyEmail_isAllowed() {
        List<String> errors = validator.validate(validRequestBuilder().customerEmail("").build());
        assertEquals(List.of(), errors);
    }

    @Test
    void validate_nullEmail_isAllowed() {
        List<String> errors = validator.validate(validRequestBuilder().customerEmail(null).build());
        assertEquals(List.of(), errors);
    }

    @Test
    void validate_creditCardWithoutLastFour_returnsError() {
        List<String> errors = validator.validate(validRequestBuilder().cardLastFour(null).build());
        assertTrue(errors.contains("Card last four digits required for card payments"));
    }

    @Test
    void validate_debitWithoutLastFour_returnsError() {
        List<String> errors = validator.validate(
                validRequestBuilder().paymentType(PaymentType.DEBIT).cardLastFour("").build());
        assertTrue(errors.contains("Card last four digits required for card payments"));
    }

    @Test
    void validate_cardLastFourNotDigits_returnsError() {
        List<String> errors = validator.validate(validRequestBuilder().cardLastFour("12ab").build());
        assertTrue(errors.contains("Card last four must be exactly 4 digits"));
    }

    @Test
    void validate_cardLastFourWrongLength_returnsError() {
        List<String> errors = validator.validate(validRequestBuilder().cardLastFour("123").build());
        assertTrue(errors.contains("Card last four must be exactly 4 digits"));
    }

    @Test
    void validate_wireWithoutCardInfo_isAllowed() {
        List<String> errors = validator.validate(
                validRequestBuilder().paymentType(PaymentType.WIRE).cardLastFour(null).build());
        assertEquals(List.of(), errors);
    }

    @Test
    void validate_descriptionTooLong_returnsError() {
        String longDescription = "x".repeat(501);
        List<String> errors = validator.validate(
                validRequestBuilder().description(longDescription).build());
        assertTrue(errors.contains("Description must not exceed 500 characters"));
    }

    @Test
    void validate_descriptionAtMaxLength_isAllowed() {
        String description = "x".repeat(500);
        List<String> errors = validator.validate(
                validRequestBuilder().description(description).build());
        assertEquals(List.of(), errors);
    }

    @Test
    void validate_multipleErrors_returnsAll() {
        PaymentRequest request = PaymentRequest.builder()
                .amount(new BigDecimal("-1"))
                .currency("FAKE")
                .build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Merchant ID is required"));
        assertTrue(errors.contains("Amount must be positive"));
        assertTrue(errors.contains("Currency must be a 3-letter ISO code"));
        assertTrue(errors.contains("Payment type is required"));
    }

    @Test
    void isCurrencySupported_supportedCurrency_returnsTrue() {
        assertTrue(validator.isCurrencySupported("USD"));
        assertTrue(validator.isCurrencySupported("jpy"));
    }

    @Test
    void isCurrencySupported_unsupportedOrNull_returnsFalse() {
        assertFalse(validator.isCurrencySupported("ZWL"));
        assertFalse(validator.isCurrencySupported(null));
    }

    @Test
    void isValidCardLastFour_validDigits_returnsTrue() {
        assertTrue(validator.isValidCardLastFour("0000"));
        assertTrue(validator.isValidCardLastFour("9999"));
    }

    @Test
    void isValidCardLastFour_invalidValues_returnsFalse() {
        assertFalse(validator.isValidCardLastFour(null));
        assertFalse(validator.isValidCardLastFour("123"));
        assertFalse(validator.isValidCardLastFour("12345"));
        assertFalse(validator.isValidCardLastFour("abcd"));
    }
}
