package com.bigfake.payments.util;

import com.bigfake.payments.model.dto.PaymentRequest;
import com.bigfake.payments.model.enums.PaymentType;
import com.bigfake.payments.testsupport.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the legacy {@link PaymentValidator} rule set.
 */
class PaymentValidatorTest {

    private PaymentValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PaymentValidator();
    }

    private static String repeat(String value, int times) {
        return IntStream.range(0, times).mapToObj(i -> value).collect(Collectors.joining());
    }

    @Test
    void acceptsAFullyPopulatedRequest() {
        assertTrue(validator.validate(TestFixtures.creditCardRequest().build()).isEmpty());
    }

    @Test
    void reportsASingleErrorForANullRequest() {
        assertEquals(List.of("Payment request cannot be null"), validator.validate(null));
    }

    @Test
    void requiresAMerchantId() {
        List<String> errors = validator.validate(TestFixtures.creditCardRequest().merchantId(null).build());

        assertEquals(List.of("Merchant ID is required"), errors);
    }

    @Test
    void requiresAnAmount() {
        List<String> errors = validator.validate(TestFixtures.creditCardRequest().amount(null).build());

        assertEquals(List.of("Amount is required"), errors);
    }

    @ParameterizedTest(name = "amount {0} is not positive")
    @ValueSource(strings = {"0.00", "-1.00"})
    void rejectsNonPositiveAmounts(String amount) {
        List<String> errors = validator.validate(
                TestFixtures.creditCardRequest().amount(new BigDecimal(amount)).build());

        assertEquals(List.of("Amount must be positive"), errors);
    }

    @Test
    void rejectsAmountsAboveTheAbsoluteMaximum() {
        List<String> errors = validator.validate(
                TestFixtures.creditCardRequest().amount(new BigDecimal("1000000.01")).build());

        assertEquals(List.of("Amount exceeds absolute maximum of 1000000.00"), errors);
    }

    @Test
    void allowsAmountsExactlyAtTheAbsoluteMaximumAndWithExtraDecimals() {
        assertTrue(validator.validate(
                TestFixtures.creditCardRequest().amount(new BigDecimal("1000000.00")).build()).isEmpty());
        assertTrue(validator.validate(
                TestFixtures.creditCardRequest().amount(new BigDecimal("10.12345")).build()).isEmpty());
    }

    @ParameterizedTest(name = "currency \"{0}\" is required")
    @ValueSource(strings = {""})
    void requiresACurrency(String currency) {
        List<String> errors = validator.validate(
                TestFixtures.creditCardRequest().currency(currency).build());

        assertEquals(List.of("Currency is required"), errors);
    }

    @Test
    void requiresANonNullCurrency() {
        List<String> errors = validator.validate(TestFixtures.creditCardRequest().currency(null).build());

        assertEquals(List.of("Currency is required"), errors);
    }

    @Test
    void rejectsCurrencyCodesThatAreNotThreeLetters() {
        List<String> errors = validator.validate(
                TestFixtures.creditCardRequest().currency("US").build());

        assertEquals(2, errors.size());
        assertTrue(errors.contains("Currency must be a 3-letter ISO code"));
        assertTrue(errors.contains("Unsupported currency: US"));
    }

    @Test
    void rejectsUnknownThreeLetterCurrencies() {
        List<String> errors = validator.validate(
                TestFixtures.creditCardRequest().currency("XYZ").build());

        assertEquals(List.of("Unsupported currency: XYZ"), errors);
    }

    @Test
    void acceptsSupportedCurrenciesInLowerCase() {
        assertTrue(validator.validate(TestFixtures.creditCardRequest().currency("usd").build()).isEmpty());
    }

    @Test
    void requiresAPaymentType() {
        List<String> errors = validator.validate(
                TestFixtures.creditCardRequest().paymentType(null).build());

        assertEquals(List.of("Payment type is required"), errors);
    }

    @Test
    void rejectsMalformedEmailAddresses() {
        List<String> errors = validator.validate(
                TestFixtures.creditCardRequest().customerEmail("not-an-email").build());

        assertEquals(List.of("Invalid email format: not-an-email"), errors);
    }

    @Test
    void allowsMissingOrEmptyEmailAddresses() {
        assertTrue(validator.validate(
                TestFixtures.creditCardRequest().customerEmail(null).build()).isEmpty());
        assertTrue(validator.validate(
                TestFixtures.creditCardRequest().customerEmail("").build()).isEmpty());
    }

    @ParameterizedTest(name = "{0} payments require card digits")
    @EnumSource(value = PaymentType.class, names = {"CREDIT_CARD", "DEBIT"})
    void requiresCardDigitsForCardPayments(PaymentType type) {
        PaymentRequest missing = TestFixtures.creditCardRequest()
                .paymentType(type).cardLastFour(null).build();
        PaymentRequest empty = TestFixtures.creditCardRequest()
                .paymentType(type).cardLastFour("").build();
        PaymentRequest malformed = TestFixtures.creditCardRequest()
                .paymentType(type).cardLastFour("12a4").build();

        assertEquals(List.of("Card last four digits required for card payments"),
                validator.validate(missing));
        assertEquals(List.of("Card last four digits required for card payments"),
                validator.validate(empty));
        assertEquals(List.of("Card last four must be exactly 4 digits"), validator.validate(malformed));
    }

    @ParameterizedTest(name = "{0} payments do not require card digits")
    @EnumSource(value = PaymentType.class, names = {"WIRE", "ACH"})
    void doesNotRequireCardDigitsForBankTransfers(PaymentType type) {
        assertTrue(validator.validate(TestFixtures.creditCardRequest()
                .paymentType(type)
                .cardLastFour(null)
                .build()).isEmpty());
    }

    @Test
    void rejectsOverlyLongDescriptions() {
        List<String> errors = validator.validate(
                TestFixtures.creditCardRequest().description(repeat("x", 501)).build());

        assertEquals(List.of("Description must not exceed 500 characters"), errors);
    }

    @Test
    void allowsDescriptionsAtTheLengthLimit() {
        assertTrue(validator.validate(
                TestFixtures.creditCardRequest().description(repeat("x", 500)).build()).isEmpty());
    }

    @Test
    void collectsEveryViolationOfAThoroughlyInvalidRequest() {
        List<String> errors = validator.validate(PaymentRequest.builder()
                .merchantId(null)
                .amount(new BigDecimal("-5.00"))
                .currency("ZZZZ")
                .paymentType(null)
                .customerEmail("bad email")
                .build());

        assertEquals(6, errors.size());
        assertTrue(errors.contains("Merchant ID is required"));
        assertTrue(errors.contains("Amount must be positive"));
        assertTrue(errors.contains("Payment type is required"));
        assertTrue(errors.contains("Currency must be a 3-letter ISO code"));
        assertTrue(errors.contains("Unsupported currency: ZZZZ"));
        assertTrue(errors.contains("Invalid email format: bad email"));
    }

    @Test
    void isCurrencySupportedMatchesTheSupportedList() {
        assertTrue(validator.isCurrencySupported("usd"));
        assertTrue(validator.isCurrencySupported("BRL"));
        assertFalse(validator.isCurrencySupported("XYZ"));
        assertFalse(validator.isCurrencySupported(null));
    }

    @Test
    void isValidCardLastFourAcceptsExactlyFourDigits() {
        assertTrue(validator.isValidCardLastFour("4242"));
        assertFalse(validator.isValidCardLastFour("424"));
        assertFalse(validator.isValidCardLastFour("42425"));
        assertFalse(validator.isValidCardLastFour("42a2"));
        assertFalse(validator.isValidCardLastFour(null));
    }
}
