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
 * Unit tests for the legacy manual validation rules in PaymentValidator.
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
    void validate_validRequestHasNoErrors() {
        assertEquals(List.of(), validator.validate(validRequest().build()));
    }

    @Test
    void validate_nullRequestReportsSingleError() {
        assertEquals(List.of("Payment request cannot be null"), validator.validate(null));
    }

    @Test
    void validate_missingMerchantId() {
        List<String> errors = validator.validate(validRequest().merchantId(null).build());

        assertEquals(List.of("Merchant ID is required"), errors);
    }

    @Test
    void validate_missingAmount() {
        List<String> errors = validator.validate(validRequest().amount(null).build());

        assertEquals(List.of("Amount is required"), errors);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-0.01", "-100.00"})
    void validate_nonPositiveAmount(String amount) {
        List<String> errors = validator.validate(validRequest().amount(new BigDecimal(amount)).build());

        assertEquals(List.of("Amount must be positive"), errors);
    }

    @Test
    void validate_amountAboveAbsoluteMaximum() {
        List<String> errors = validator.validate(validRequest().amount(new BigDecimal("1000000.01")).build());

        assertEquals(List.of("Amount exceeds absolute maximum of 1000000.00"), errors);
    }

    @Test
    void validate_amountExactlyAtAbsoluteMaximumIsAllowed() {
        List<String> errors = validator.validate(validRequest().amount(new BigDecimal("1000000.00")).build());

        assertEquals(List.of(), errors);
    }

    @Test
    void validate_amountWithMoreThanTwoDecimalsIsAcceptedButLogged() {
        List<String> errors = validator.validate(validRequest().amount(new BigDecimal("10.12345")).build());

        assertEquals(List.of(), errors);
    }

    @Test
    void validate_missingCurrency() {
        assertEquals(List.of("Currency is required"), validator.validate(validRequest().currency(null).build()));
        assertEquals(List.of("Currency is required"), validator.validate(validRequest().currency("").build()));
    }

    @Test
    void validate_currencyWithWrongLengthReportsBothLengthAndSupportErrors() {
        List<String> errors = validator.validate(validRequest().currency("US").build());

        assertEquals(List.of("Currency must be a 3-letter ISO code", "Unsupported currency: US"), errors);
    }

    @Test
    void validate_unsupportedThreeLetterCurrency() {
        List<String> errors = validator.validate(validRequest().currency("XYZ").build());

        assertEquals(List.of("Unsupported currency: XYZ"), errors);
    }

    @Test
    void validate_lowercaseCurrencyIsSupported() {
        assertEquals(List.of(), validator.validate(validRequest().currency("eur").build()));
    }

    @Test
    void validate_missingPaymentType() {
        List<String> errors = validator.validate(validRequest().paymentType(null).cardLastFour(null).build());

        assertEquals(List.of("Payment type is required"), errors);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-email", "missing-at.example.com", "spaces in@example.com"})
    void validate_invalidEmailFormat(String email) {
        List<String> errors = validator.validate(validRequest().customerEmail(email).build());

        assertEquals(List.of("Invalid email format: " + email), errors);
    }

    @Test
    void validate_blankOrNullEmailIsSkipped() {
        assertEquals(List.of(), validator.validate(validRequest().customerEmail(null).build()));
        assertEquals(List.of(), validator.validate(validRequest().customerEmail("").build()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREDIT_CARD", "DEBIT"})
    void validate_cardPaymentsRequireLastFour(String type) {
        PaymentType paymentType = PaymentType.valueOf(type);

        assertEquals(List.of("Card last four digits required for card payments"),
                validator.validate(validRequest().paymentType(paymentType).cardLastFour(null).build()));
        assertEquals(List.of("Card last four digits required for card payments"),
                validator.validate(validRequest().paymentType(paymentType).cardLastFour("").build()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "12345", "abcd", "12a4"})
    void validate_cardLastFourMustBeFourDigits(String lastFour) {
        List<String> errors = validator.validate(validRequest().cardLastFour(lastFour).build());

        assertEquals(List.of("Card last four must be exactly 4 digits"), errors);
    }

    @ParameterizedTest
    @ValueSource(strings = {"WIRE", "ACH"})
    void validate_nonCardPaymentsDoNotRequireLastFour(String type) {
        List<String> errors = validator.validate(
                validRequest().paymentType(PaymentType.valueOf(type)).cardLastFour(null).build());

        assertEquals(List.of(), errors);
    }

    @Test
    void validate_descriptionLongerThan500Characters() {
        String description = repeat("d", 501);

        List<String> errors = validator.validate(validRequest().description(description).build());

        assertEquals(List.of("Description must not exceed 500 characters"), errors);
    }

    @Test
    void validate_descriptionOf500CharactersIsAllowed() {
        List<String> errors = validator.validate(validRequest().description(repeat("d", 500)).build());

        assertEquals(List.of(), errors);
    }

    @Test
    void validate_collectsAllErrorsForACompletelyInvalidRequest() {
        PaymentRequest request = PaymentRequest.builder()
                .amount(new BigDecimal("-5.00"))
                .currency("ZZ")
                .customerEmail("bad-email")
                .build();

        List<String> errors = validator.validate(request);

        assertEquals(List.of(
                "Merchant ID is required",
                "Amount must be positive",
                "Currency must be a 3-letter ISO code",
                "Unsupported currency: ZZ",
                "Payment type is required",
                "Invalid email format: bad-email"), errors);
    }

    @ParameterizedTest
    @ValueSource(strings = {"USD", "eur", "BRL"})
    void isCurrencySupported_returnsTrueForKnownCurrencies(String currency) {
        assertTrue(validator.isCurrencySupported(currency));
    }

    @Test
    void isCurrencySupported_returnsFalseForUnknownOrNullCurrency() {
        assertFalse(validator.isCurrencySupported("XYZ"));
        assertFalse(validator.isCurrencySupported(null));
    }

    @Test
    void isValidCardLastFour_acceptsExactlyFourDigits() {
        assertTrue(validator.isValidCardLastFour("0000"));
        assertTrue(validator.isValidCardLastFour("4242"));
    }

    @Test
    void isValidCardLastFour_rejectsAnythingElse() {
        assertFalse(validator.isValidCardLastFour(null));
        assertFalse(validator.isValidCardLastFour(""));
        assertFalse(validator.isValidCardLastFour("424"));
        assertFalse(validator.isValidCardLastFour("42424"));
        assertFalse(validator.isValidCardLastFour("42x2"));
    }

    private static String repeat(String value, int times) {
        StringBuilder builder = new StringBuilder(value.length() * times);
        for (int i = 0; i < times; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
