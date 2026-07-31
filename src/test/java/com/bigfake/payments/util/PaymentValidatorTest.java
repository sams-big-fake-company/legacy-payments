package com.bigfake.payments.util;

import com.bigfake.payments.model.dto.PaymentRequest;
import com.bigfake.payments.model.enums.PaymentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the legacy manual validation rules in PaymentValidator.
 */
class PaymentValidatorTest {

    private PaymentValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PaymentValidator();
    }

    private PaymentRequest.PaymentRequestBuilder validRequest() {
        return PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("99.99"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .customerEmail("customer@example.com");
    }

    @Test
    void validate_returnsNoErrorsForValidRequest() {
        assertEquals(List.of(), validator.validate(validRequest().build()));
    }

    @Test
    void validate_nullRequestReturnsSingleError() {
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
    void validate_amountAtAbsoluteMaximumIsAllowed() {
        assertEquals(List.of(), validator.validate(validRequest().amount(new BigDecimal("1000000.00")).build()));
    }

    @Test
    void validate_amountWithExtraDecimalsIsAllowed() {
        assertEquals(List.of(), validator.validate(validRequest().amount(new BigDecimal("10.12345")).build()));
    }

    @Test
    void validate_missingCurrency() {
        assertEquals(List.of("Currency is required"), validator.validate(validRequest().currency(null).build()));
        assertEquals(List.of("Currency is required"), validator.validate(validRequest().currency("").build()));
    }

    @Test
    void validate_currencyWithWrongLengthReportsBothIsoAndSupportErrors() {
        List<String> errors = validator.validate(validRequest().currency("US").build());

        assertEquals(List.of("Currency must be a 3-letter ISO code", "Unsupported currency: US"), errors);
    }

    @Test
    void validate_unsupportedThreeLetterCurrency() {
        List<String> errors = validator.validate(validRequest().currency("XYZ").build());

        assertEquals(List.of("Unsupported currency: XYZ"), errors);
    }

    @Test
    void validate_lowercaseCurrencyIsAccepted() {
        assertEquals(List.of(), validator.validate(validRequest().currency("eur").build()));
    }

    @Test
    void validate_missingPaymentType() {
        List<String> errors = validator.validate(validRequest().paymentType(null).build());

        assertEquals(List.of("Payment type is required"), errors);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-email", "missing-at-sign.com", "spaces in@example.com"})
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
    @EnumSource(value = PaymentType.class, names = {"CREDIT_CARD", "DEBIT"})
    void validate_cardPaymentsRequireLastFour(PaymentType type) {
        List<String> missing = validator.validate(validRequest().paymentType(type).cardLastFour(null).build());
        List<String> empty = validator.validate(validRequest().paymentType(type).cardLastFour("").build());

        assertEquals(List.of("Card last four digits required for card payments"), missing);
        assertEquals(List.of("Card last four digits required for card payments"), empty);
    }

    @ParameterizedTest
    @ValueSource(strings = {"12", "12345", "abcd", "12a4"})
    void validate_cardLastFourMustBeFourDigits(String lastFour) {
        List<String> errors = validator.validate(validRequest().cardLastFour(lastFour).build());

        assertEquals(List.of("Card last four must be exactly 4 digits"), errors);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentType.class, names = {"WIRE", "ACH"})
    void validate_nonCardPaymentsDoNotRequireLastFour(PaymentType type) {
        assertEquals(List.of(), validator.validate(validRequest().paymentType(type).cardLastFour(null).build()));
    }

    @Test
    void validate_descriptionTooLong() {
        String description = repeat("x", 501);

        List<String> errors = validator.validate(validRequest().description(description).build());

        assertEquals(List.of("Description must not exceed 500 characters"), errors);
    }

    @Test
    void validate_descriptionAtLimitIsAllowed() {
        assertEquals(List.of(), validator.validate(validRequest().description(repeat("x", 500)).build()));
    }

    @Test
    void validate_accumulatesMultipleErrors() {
        PaymentRequest request = PaymentRequest.builder()
                .amount(new BigDecimal("-5.00"))
                .currency("ZZZ")
                .paymentType(PaymentType.DEBIT)
                .customerEmail("bad-email")
                .build();

        List<String> errors = validator.validate(request);

        assertEquals(List.of(
                "Merchant ID is required",
                "Amount must be positive",
                "Unsupported currency: ZZZ",
                "Invalid email format: bad-email",
                "Card last four digits required for card payments"), errors);
    }

    @Test
    void isCurrencySupported_checksTheLegacyCurrencyList() {
        assertTrue(validator.isCurrencySupported("usd"));
        assertTrue(validator.isCurrencySupported("BRL"));
        assertFalse(validator.isCurrencySupported("XYZ"));
        assertFalse(validator.isCurrencySupported(null));
    }

    @Test
    void isValidCardLastFour_requiresExactlyFourDigits() {
        assertTrue(validator.isValidCardLastFour("0000"));
        assertFalse(validator.isValidCardLastFour("123"));
        assertFalse(validator.isValidCardLastFour("12345"));
        assertFalse(validator.isValidCardLastFour("12x4"));
        assertFalse(validator.isValidCardLastFour(null));
    }

    private static String repeat(String value, int times) {
        StringBuilder builder = new StringBuilder(value.length() * times);
        for (int i = 0; i < times; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
