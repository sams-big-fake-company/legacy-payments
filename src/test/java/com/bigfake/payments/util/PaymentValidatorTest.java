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

    private static PaymentRequest validRequest() {
        return PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("99.99"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .customerEmail("customer@example.com")
                .description("A valid description")
                .build();
    }

    @Test
    void returnsNoErrorsForAValidRequest() {
        assertEquals(List.of(), validator.validate(validRequest()));
    }

    @Test
    void reportsASingleErrorForANullRequestWithoutInspectingOtherRules() {
        assertEquals(List.of("Payment request cannot be null"), validator.validate(null));
    }

    @Test
    void reportsEveryMissingRequiredFieldAtOnce() {
        List<String> errors = validator.validate(PaymentRequest.builder().build());

        assertEquals(List.of(
                "Merchant ID is required",
                "Amount is required",
                "Currency is required",
                "Payment type is required"), errors);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-0.01", "-100.00"})
    void rejectsNonPositiveAmounts(String amount) {
        PaymentRequest request = validRequest();
        request.setAmount(new BigDecimal(amount));

        assertTrue(validator.validate(request).contains("Amount must be positive"));
    }

    @Test
    void acceptsTheAbsoluteMaximumButRejectsAnythingAboveIt() {
        PaymentRequest atMax = validRequest();
        atMax.setAmount(new BigDecimal("1000000.00"));
        assertEquals(List.of(), validator.validate(atMax));

        PaymentRequest overMax = validRequest();
        overMax.setAmount(new BigDecimal("1000000.01"));
        assertEquals(List.of("Amount exceeds absolute maximum of 1000000.00"), validator.validate(overMax));
    }

    @Test
    void allowsMoreThanTwoDecimalPlacesButOnlyLogsAWarning() {
        PaymentRequest request = validRequest();
        request.setAmount(new BigDecimal("10.12345"));

        assertEquals(List.of(), validator.validate(request));
    }

    @Test
    void rejectsAnEmptyCurrencyAsMissing() {
        PaymentRequest request = validRequest();
        request.setCurrency("");

        assertEquals(List.of("Currency is required"), validator.validate(request));
    }

    @Test
    void rejectsCurrencyCodesThatAreNotThreeLetters() {
        PaymentRequest request = validRequest();
        request.setCurrency("US");

        assertEquals(List.of(
                "Currency must be a 3-letter ISO code",
                "Unsupported currency: US"), validator.validate(request));
    }

    @Test
    void rejectsUnsupportedThreeLetterCurrencies() {
        PaymentRequest request = validRequest();
        request.setCurrency("XYZ");

        assertEquals(List.of("Unsupported currency: XYZ"), validator.validate(request));
    }

    @Test
    void acceptsSupportedCurrenciesRegardlessOfCase() {
        PaymentRequest request = validRequest();
        request.setCurrency("eur");

        assertEquals(List.of(), validator.validate(request));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-email", "missing-at.example.com", "spaces @example.com", "user@"})
    void rejectsMalformedEmails(String email) {
        PaymentRequest request = validRequest();
        request.setCustomerEmail(email);

        assertEquals(List.of("Invalid email format: " + email), validator.validate(request));
    }

    @ParameterizedTest
    @ValueSource(strings = {"user@example.com", "first.last+tag@sub.example.co.uk", "u_1-2@example-host.io"})
    void acceptsWellFormedEmails(String email) {
        PaymentRequest request = validRequest();
        request.setCustomerEmail(email);

        assertEquals(List.of(), validator.validate(request));
    }

    @Test
    void skipsEmailValidationWhenEmailIsNullOrEmpty() {
        PaymentRequest nullEmail = validRequest();
        nullEmail.setCustomerEmail(null);
        assertEquals(List.of(), validator.validate(nullEmail));

        PaymentRequest emptyEmail = validRequest();
        emptyEmail.setCustomerEmail("");
        assertEquals(List.of(), validator.validate(emptyEmail));
    }

    @ParameterizedTest
    @EnumSource(value = PaymentType.class, names = {"CREDIT_CARD", "DEBIT"})
    void requiresCardLastFourForCardPayments(PaymentType type) {
        PaymentRequest missing = validRequest();
        missing.setPaymentType(type);
        missing.setCardLastFour(null);
        assertEquals(List.of("Card last four digits required for card payments"), validator.validate(missing));

        PaymentRequest empty = validRequest();
        empty.setPaymentType(type);
        empty.setCardLastFour("");
        assertEquals(List.of("Card last four digits required for card payments"), validator.validate(empty));
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "12345", "abcd", "12a4", " 123"})
    void rejectsCardLastFourThatIsNotExactlyFourDigits(String lastFour) {
        PaymentRequest request = validRequest();
        request.setCardLastFour(lastFour);

        assertEquals(List.of("Card last four must be exactly 4 digits"), validator.validate(request));
    }

    @ParameterizedTest
    @EnumSource(value = PaymentType.class, names = {"WIRE", "ACH"})
    void doesNotRequireCardLastFourForNonCardPayments(PaymentType type) {
        PaymentRequest request = validRequest();
        request.setPaymentType(type);
        request.setCardLastFour(null);

        assertEquals(List.of(), validator.validate(request));
    }

    @Test
    void acceptsDescriptionsUpToFiveHundredCharactersAndRejectsLongerOnes() {
        PaymentRequest atLimit = validRequest();
        atLimit.setDescription(repeat("a", 500));
        assertEquals(List.of(), validator.validate(atLimit));

        PaymentRequest overLimit = validRequest();
        overLimit.setDescription(repeat("a", 501));
        assertEquals(List.of("Description must not exceed 500 characters"), validator.validate(overLimit));
    }

    @Test
    void accumulatesMultipleErrorsForAThoroughlyInvalidRequest() {
        PaymentRequest request = PaymentRequest.builder()
                .amount(new BigDecimal("-5.00"))
                .currency("XYZ")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("12")
                .customerEmail("bad-email")
                .build();

        assertEquals(List.of(
                "Merchant ID is required",
                "Amount must be positive",
                "Unsupported currency: XYZ",
                "Invalid email format: bad-email",
                "Card last four must be exactly 4 digits"), validator.validate(request));
    }

    @ParameterizedTest
    @ValueSource(strings = {"USD", "eur", "GbP", "CAD", "AUD", "JPY", "CHF", "CNY", "INR", "BRL"})
    void isCurrencySupportedAcceptsKnownCodesRegardlessOfCase(String currency) {
        assertTrue(validator.isCurrencySupported(currency));
    }

    @Test
    void isCurrencySupportedRejectsUnknownCodesAndNull() {
        assertFalse(validator.isCurrencySupported("XYZ"));
        assertFalse(validator.isCurrencySupported(""));
        assertFalse(validator.isCurrencySupported(null));
    }

    @Test
    void isValidCardLastFourAcceptsExactlyFourDigits() {
        assertTrue(validator.isValidCardLastFour("0000"));
        assertTrue(validator.isValidCardLastFour("4242"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "123", "12345", "12a4"})
    void isValidCardLastFourRejectsAnythingElse(String lastFour) {
        assertFalse(validator.isValidCardLastFour(lastFour));
    }

    @Test
    void isValidCardLastFourRejectsNull() {
        assertFalse(validator.isValidCardLastFour(null));
    }

    private static String repeat(String s, int times) {
        return Stream.generate(() -> s).limit(times).collect(Collectors.joining());
    }
}
