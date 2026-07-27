package com.bigfake.payments.util;

import com.bigfake.payments.model.dto.PaymentRequest;
import com.bigfake.payments.model.enums.PaymentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for PaymentValidator.
 */
class PaymentValidatorTest {

    private PaymentValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PaymentValidator();
    }

    private static PaymentRequest.PaymentRequestBuilder validRequest() {
        return PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("25.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .customerEmail("buyer@example.com");
    }

    @Test
    void validate_validCardRequest_returnsNoErrors() {
        assertEquals(List.of(), validator.validate(validRequest().build()));
    }

    @Test
    void validate_nullRequest_returnsSingleError() {
        assertEquals(List.of("Payment request cannot be null"), validator.validate(null));
    }

    @Test
    void validate_missingMerchantId_returnsError() {
        List<String> errors = validator.validate(validRequest().merchantId(null).build());

        assertEquals(List.of("Merchant ID is required"), errors);
    }

    @Test
    void validate_missingAmount_returnsError() {
        List<String> errors = validator.validate(validRequest().amount(null).build());

        assertEquals(List.of("Amount is required"), errors);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-0.01", "-100.00"})
    void validate_nonPositiveAmount_returnsError(String amount) {
        List<String> errors = validator.validate(validRequest().amount(new BigDecimal(amount)).build());

        assertEquals(List.of("Amount must be positive"), errors);
    }

    @Test
    void validate_amountAboveAbsoluteMaximum_returnsError() {
        List<String> errors = validator.validate(validRequest().amount(new BigDecimal("1000000.01")).build());

        assertEquals(List.of("Amount exceeds absolute maximum of 1000000.00"), errors);
    }

    @Test
    void validate_amountAtAbsoluteMaximum_isAccepted() {
        List<String> errors = validator.validate(validRequest().amount(new BigDecimal("1000000.00")).build());

        assertEquals(List.of(), errors);
    }

    @Test
    void validate_amountWithMoreThanTwoDecimals_isStillValid() {
        List<String> errors = validator.validate(validRequest().amount(new BigDecimal("10.12345")).build());

        assertEquals(List.of(), errors);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void validate_blankCurrency_returnsError(String currency) {
        List<String> errors = validator.validate(validRequest().currency(currency).build());

        assertFalse(errors.isEmpty());
    }

    @Test
    void validate_nullCurrency_returnsError() {
        List<String> errors = validator.validate(validRequest().currency(null).build());

        assertEquals(List.of("Currency is required"), errors);
    }

    @Test
    void validate_currencyWithWrongLength_returnsFormatAndSupportErrors() {
        List<String> errors = validator.validate(validRequest().currency("US").build());

        assertEquals(List.of("Currency must be a 3-letter ISO code", "Unsupported currency: US"), errors);
    }

    @Test
    void validate_unsupportedCurrency_returnsError() {
        List<String> errors = validator.validate(validRequest().currency("ZWL").build());

        assertEquals(List.of("Unsupported currency: ZWL"), errors);
    }

    @Test
    void validate_lowercaseSupportedCurrency_isAccepted() {
        List<String> errors = validator.validate(validRequest().currency("eur").build());

        assertEquals(List.of(), errors);
    }

    @Test
    void validate_missingPaymentType_returnsError() {
        List<String> errors = validator.validate(validRequest().paymentType(null).build());

        assertEquals(List.of("Payment type is required"), errors);
    }

    @Test
    void validate_invalidEmail_returnsError() {
        List<String> errors = validator.validate(validRequest().customerEmail("not-an-email").build());

        assertEquals(List.of("Invalid email format: not-an-email"), errors);
    }

    @Test
    void validate_emptyEmail_isSkipped() {
        assertEquals(List.of(), validator.validate(validRequest().customerEmail("").build()));
    }

    @Test
    void validate_emailWithSurroundingWhitespace_returnsError() {
        List<String> errors = validator.validate(validRequest().customerEmail(" a@b.co ").build());

        assertEquals(List.of("Invalid email format:  a@b.co "), errors);
    }

    @Test
    void validate_nullEmail_isAccepted() {
        List<String> errors = validator.validate(validRequest().customerEmail(null).build());

        assertEquals(List.of(), errors);
    }

    @ParameterizedTest
    @CsvSource({"CREDIT_CARD", "DEBIT"})
    void validate_cardPaymentWithoutCardLastFour_returnsError(PaymentType type) {
        List<String> errors = validator.validate(validRequest().paymentType(type).cardLastFour(null).build());

        assertEquals(List.of("Card last four digits required for card payments"), errors);
    }

    @ParameterizedTest
    @CsvSource({"CREDIT_CARD", "DEBIT"})
    void validate_cardPaymentWithEmptyCardLastFour_returnsError(PaymentType type) {
        List<String> errors = validator.validate(validRequest().paymentType(type).cardLastFour("").build());

        assertEquals(List.of("Card last four digits required for card payments"), errors);
    }

    @ParameterizedTest
    @ValueSource(strings = {"12", "12345", "abcd"})
    void validate_cardPaymentWithMalformedCardLastFour_returnsError(String lastFour) {
        List<String> errors = validator.validate(validRequest().cardLastFour(lastFour).build());

        assertEquals(List.of("Card last four must be exactly 4 digits"), errors);
    }

    @ParameterizedTest
    @CsvSource({"WIRE", "ACH"})
    void validate_nonCardPaymentWithoutCardDetails_isAccepted(PaymentType type) {
        List<String> errors = validator.validate(validRequest().paymentType(type).cardLastFour(null).build());

        assertEquals(List.of(), errors);
    }

    @Test
    void validate_descriptionTooLong_returnsError() {
        String description = repeat("x", 501);

        List<String> errors = validator.validate(validRequest().description(description).build());

        assertEquals(List.of("Description must not exceed 500 characters"), errors);
    }

    @Test
    void validate_descriptionAtMaxLength_isAccepted() {
        List<String> errors = validator.validate(validRequest().description(repeat("x", 500)).build());

        assertEquals(List.of(), errors);
    }

    @Test
    void validate_multipleProblems_reportsAllErrors() {
        PaymentRequest request = PaymentRequest.builder()
                .amount(new BigDecimal("-5.00"))
                .currency("ZZZ")
                .customerEmail("bad-email")
                .build();

        List<String> errors = validator.validate(request);

        assertEquals(List.of(
                "Merchant ID is required",
                "Amount must be positive",
                "Unsupported currency: ZZZ",
                "Payment type is required",
                "Invalid email format: bad-email"), errors);
    }

    @ParameterizedTest
    @ValueSource(strings = {"USD", "eur", "GbP", "JPY", "BRL"})
    void isCurrencySupported_supportedCodes_returnTrue(String currency) {
        assertTrue(validator.isCurrencySupported(currency));
    }

    @Test
    void isCurrencySupported_nullOrUnknown_returnsFalse() {
        assertFalse(validator.isCurrencySupported(null));
        assertFalse(validator.isCurrencySupported("ZWL"));
    }

    @Test
    void isValidCardLastFour_acceptsFourDigitsOnly() {
        assertTrue(validator.isValidCardLastFour("0000"));
        assertFalse(validator.isValidCardLastFour(null));
        assertFalse(validator.isValidCardLastFour("123"));
        assertFalse(validator.isValidCardLastFour("12345"));
        assertFalse(validator.isValidCardLastFour("12a4"));
    }

    private static String repeat(String value, int times) {
        StringBuilder builder = new StringBuilder(value.length() * times);
        for (int i = 0; i < times; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
