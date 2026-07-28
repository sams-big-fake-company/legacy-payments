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
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
                .customerEmail("customer@example.com");
    }

    @Test
    void validRequestProducesNoErrors() {
        assertEquals(List.of(), validator.validate(validRequest().build()));
    }

    @Test
    void nullRequestProducesSingleError() {
        assertEquals(List.of("Payment request cannot be null"), validator.validate(null));
    }

    @Test
    void missingMerchantIdIsReported() {
        List<String> errors = validator.validate(validRequest().merchantId(null).build());

        assertEquals(List.of("Merchant ID is required"), errors);
    }

    @Test
    void missingAmountIsReported() {
        List<String> errors = validator.validate(validRequest().amount(null).build());

        assertEquals(List.of("Amount is required"), errors);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-0.01", "-100.00"})
    void nonPositiveAmountIsReported(String amount) {
        List<String> errors = validator.validate(validRequest().amount(new BigDecimal(amount)).build());

        assertEquals(List.of("Amount must be positive"), errors);
    }

    @Test
    void amountAboveAbsoluteMaximumIsReported() {
        List<String> errors = validator.validate(validRequest().amount(new BigDecimal("1000000.01")).build());

        assertEquals(List.of("Amount exceeds absolute maximum of 1000000.00"), errors);
    }

    @Test
    void amountExactlyAtAbsoluteMaximumIsAccepted() {
        assertEquals(List.of(), validator.validate(validRequest().amount(new BigDecimal("1000000.00")).build()));
    }

    @Test
    void amountWithMoreThanTwoDecimalPlacesIsAcceptedButNotAnError() {
        assertEquals(List.of(), validator.validate(validRequest().amount(new BigDecimal("10.12345")).build()));
    }

    @Test
    void missingCurrencyIsReported() {
        assertEquals(List.of("Currency is required"), validator.validate(validRequest().currency(null).build()));
    }

    @Test
    void emptyCurrencyIsReported() {
        assertEquals(List.of("Currency is required"), validator.validate(validRequest().currency("").build()));
    }

    @Test
    void currencyWithWrongLengthIsReportedTwice() {
        List<String> errors = validator.validate(validRequest().currency("US").build());

        assertEquals(List.of("Currency must be a 3-letter ISO code", "Unsupported currency: US"), errors);
    }

    @Test
    void unsupportedThreeLetterCurrencyIsReported() {
        assertEquals(List.of("Unsupported currency: XYZ"), validator.validate(validRequest().currency("XYZ").build()));
    }

    @Test
    void lowercaseSupportedCurrencyIsAccepted() {
        assertEquals(List.of(), validator.validate(validRequest().currency("eur").build()));
    }

    @Test
    void missingPaymentTypeIsReported() {
        List<String> errors = validator.validate(validRequest().paymentType(null).build());

        assertEquals(List.of("Payment type is required"), errors);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-email", "missing-at.example.com", "spaces in@example.com"})
    void invalidEmailIsReported(String email) {
        List<String> errors = validator.validate(validRequest().customerEmail(email).build());

        assertEquals(List.of("Invalid email format: " + email), errors);
    }

    @ParameterizedTest
    @ValueSource(strings = {"user@example.com", "first.last+tag@sub.example.co", "a_b-c@example"})
    void validEmailIsAccepted(String email) {
        assertEquals(List.of(), validator.validate(validRequest().customerEmail(email).build()));
    }

    @Test
    void emptyEmailSkipsFormatValidation() {
        assertEquals(List.of(), validator.validate(validRequest().customerEmail("").build()));
    }

    @Test
    void nullEmailSkipsFormatValidation() {
        assertEquals(List.of(), validator.validate(validRequest().customerEmail(null).build()));
    }

    @ParameterizedTest
    @EnumSource(value = PaymentType.class, names = {"CREDIT_CARD", "DEBIT"})
    void missingCardLastFourIsReportedForCardPayments(PaymentType type) {
        List<String> errors = validator.validate(validRequest().paymentType(type).cardLastFour(null).build());

        assertEquals(List.of("Card last four digits required for card payments"), errors);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentType.class, names = {"CREDIT_CARD", "DEBIT"})
    void emptyCardLastFourIsReportedForCardPayments(PaymentType type) {
        List<String> errors = validator.validate(validRequest().paymentType(type).cardLastFour("").build());

        assertEquals(List.of("Card last four digits required for card payments"), errors);
    }

    @ParameterizedTest
    @ValueSource(strings = {"12a4", "123", "12345", "abcd"})
    void malformedCardLastFourIsReported(String lastFour) {
        List<String> errors = validator.validate(validRequest().cardLastFour(lastFour).build());

        assertEquals(List.of("Card last four must be exactly 4 digits"), errors);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentType.class, names = {"WIRE", "ACH"})
    void cardValidationIsSkippedForNonCardPayments(PaymentType type) {
        assertEquals(List.of(), validator.validate(validRequest().paymentType(type).cardLastFour(null).build()));
    }

    @Test
    void descriptionLongerThan500CharactersIsReported() {
        String description = Stream.generate(() -> "x").limit(501).collect(Collectors.joining());

        List<String> errors = validator.validate(validRequest().description(description).build());

        assertEquals(List.of("Description must not exceed 500 characters"), errors);
    }

    @Test
    void descriptionOfExactly500CharactersIsAccepted() {
        String description = Stream.generate(() -> "x").limit(500).collect(Collectors.joining());

        assertEquals(List.of(), validator.validate(validRequest().description(description).build()));
    }

    @Test
    void multipleProblemsAreAllReported() {
        List<String> errors = validator.validate(PaymentRequest.builder()
                .amount(new BigDecimal("-5.00"))
                .currency("QQ")
                .customerEmail("bad-email")
                .build());

        assertEquals(List.of(
                "Merchant ID is required",
                "Amount must be positive",
                "Currency must be a 3-letter ISO code",
                "Unsupported currency: QQ",
                "Payment type is required",
                "Invalid email format: bad-email"), errors);
    }

    @Test
    void isCurrencySupportedAcceptsKnownCurrencyInAnyCase() {
        assertTrue(validator.isCurrencySupported("USD"));
        assertTrue(validator.isCurrencySupported("brl"));
    }

    @Test
    void isCurrencySupportedRejectsUnknownAndNull() {
        assertFalse(validator.isCurrencySupported("XYZ"));
        assertFalse(validator.isCurrencySupported(null));
    }

    @Test
    void isValidCardLastFourAcceptsExactlyFourDigits() {
        assertTrue(validator.isValidCardLastFour("0000"));
        assertTrue(validator.isValidCardLastFour("4242"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"424", "42425", "42a2", " 424"})
    void isValidCardLastFourRejectsMalformedValues(String lastFour) {
        assertFalse(validator.isValidCardLastFour(lastFour));
    }

    @Test
    void isValidCardLastFourRejectsNull() {
        assertFalse(validator.isValidCardLastFour(null));
    }
}
