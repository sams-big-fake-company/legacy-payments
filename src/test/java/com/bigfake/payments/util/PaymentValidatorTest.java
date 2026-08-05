package com.bigfake.payments.util;

import com.bigfake.payments.model.dto.PaymentRequest;
import com.bigfake.payments.model.enums.PaymentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
                .amount(new BigDecimal("99.99"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .customerEmail("customer@example.com");
    }

    @Test
    void validate_validCreditCardRequest_hasNoErrors() {
        assertEquals(List.of(), validator.validate(validRequest().build()));
    }

    @Test
    void validate_nullRequest_returnsSingleError() {
        assertEquals(List.of("Payment request cannot be null"), validator.validate(null));
    }

    @Test
    void validate_missingMerchantId_reportsError() {
        List<String> errors = validator.validate(validRequest().merchantId(null).build());

        assertEquals(List.of("Merchant ID is required"), errors);
    }

    @Nested
    class AmountValidation {

        @Test
        void nullAmount_reportsRequired() {
            assertEquals(List.of("Amount is required"),
                    validator.validate(validRequest().amount(null).build()));
        }

        @ParameterizedTest
        @ValueSource(strings = {"0.00", "-0.01", "-100.00"})
        void nonPositiveAmount_reportsMustBePositive(String amount) {
            assertEquals(List.of("Amount must be positive"),
                    validator.validate(validRequest().amount(new BigDecimal(amount)).build()));
        }

        @Test
        void amountAtAbsoluteMaximum_isAccepted() {
            assertEquals(List.of(),
                    validator.validate(validRequest().amount(new BigDecimal("1000000.00")).build()));
        }

        @Test
        void amountAboveAbsoluteMaximum_reportsError() {
            assertEquals(List.of("Amount exceeds absolute maximum of 1000000.00"),
                    validator.validate(validRequest().amount(new BigDecimal("1000000.01")).build()));
        }

        @Test
        void amountWithMoreThanTwoDecimals_isAcceptedButNotAnError() {
            assertEquals(List.of(),
                    validator.validate(validRequest().amount(new BigDecimal("10.12345")).build()));
        }
    }

    @Nested
    class CurrencyValidation {

        @Test
        void nullCurrency_reportsRequired() {
            assertEquals(List.of("Currency is required"),
                    validator.validate(validRequest().currency(null).build()));
        }

        @Test
        void emptyCurrency_reportsRequired() {
            assertEquals(List.of("Currency is required"),
                    validator.validate(validRequest().currency("").build()));
        }

        @Test
        void wrongLengthCurrency_reportsLengthAndSupportErrors() {
            assertEquals(
                    List.of("Currency must be a 3-letter ISO code", "Unsupported currency: US"),
                    validator.validate(validRequest().currency("US").build()));
        }

        @Test
        void unsupportedThreeLetterCurrency_reportsOnlySupportError() {
            assertEquals(List.of("Unsupported currency: ZZZ"),
                    validator.validate(validRequest().currency("ZZZ").build()));
        }

        @Test
        void lowercaseSupportedCurrency_isAccepted() {
            assertEquals(List.of(), validator.validate(validRequest().currency("eur").build()));
        }
    }

    @Test
    void validate_missingPaymentType_reportsError() {
        List<String> errors = validator.validate(
                validRequest().paymentType(null).cardLastFour(null).build());

        assertEquals(List.of("Payment type is required"), errors);
    }

    @Nested
    class EmailValidation {

        @ParameterizedTest
        @ValueSource(strings = {"user@example.com", "first.last+tag@sub.example.co.uk", "a_b-c@d-e.io"})
        void acceptsValidEmails(String email) {
            assertEquals(List.of(), validator.validate(validRequest().customerEmail(email).build()));
        }

        @ParameterizedTest
        @ValueSource(strings = {"not-an-email", "missing@", "@example.com", "spaces in@example.com"})
        void rejectsInvalidEmails(String email) {
            assertEquals(List.of("Invalid email format: " + email),
                    validator.validate(validRequest().customerEmail(email).build()));
        }

        @Test
        void nullEmail_isSkipped() {
            assertEquals(List.of(), validator.validate(validRequest().customerEmail(null).build()));
        }

        @Test
        void emptyEmail_isSkipped() {
            assertEquals(List.of(), validator.validate(validRequest().customerEmail("").build()));
        }
    }

    @Nested
    class CardValidation {

        @ParameterizedTest
        @CsvSource({"CREDIT_CARD", "DEBIT"})
        void cardPaymentWithoutLastFour_reportsRequired(PaymentType type) {
            assertEquals(List.of("Card last four digits required for card payments"),
                    validator.validate(validRequest().paymentType(type).cardLastFour(null).build()));
        }

        @ParameterizedTest
        @CsvSource({"CREDIT_CARD", "DEBIT"})
        void cardPaymentWithEmptyLastFour_reportsRequired(PaymentType type) {
            assertEquals(List.of("Card last four digits required for card payments"),
                    validator.validate(validRequest().paymentType(type).cardLastFour("").build()));
        }

        @ParameterizedTest
        @ValueSource(strings = {"123", "12345", "abcd", "12a4"})
        void cardPaymentWithMalformedLastFour_reportsFormatError(String lastFour) {
            assertEquals(List.of("Card last four must be exactly 4 digits"),
                    validator.validate(validRequest().cardLastFour(lastFour).build()));
        }

        @ParameterizedTest
        @CsvSource({"WIRE", "ACH"})
        void nonCardPaymentWithoutLastFour_isAccepted(PaymentType type) {
            assertEquals(List.of(),
                    validator.validate(validRequest().paymentType(type).cardLastFour(null).build()));
        }
    }

    @Test
    void validate_descriptionAtMaxLength_isAccepted() {
        String description = repeat('x', 500);

        assertEquals(List.of(), validator.validate(validRequest().description(description).build()));
    }

    @Test
    void validate_descriptionTooLong_reportsError() {
        String description = repeat('x', 501);

        assertEquals(List.of("Description must not exceed 500 characters"),
                validator.validate(validRequest().description(description).build()));
    }

    @Test
    void validate_multipleProblems_reportsAllErrorsInOrder() {
        PaymentRequest request = PaymentRequest.builder()
                .amount(new BigDecimal("-5.00"))
                .currency("ZZ")
                .paymentType(PaymentType.CREDIT_CARD)
                .customerEmail("bad-email")
                .build();

        assertEquals(List.of(
                "Merchant ID is required",
                "Amount must be positive",
                "Currency must be a 3-letter ISO code",
                "Unsupported currency: ZZ",
                "Invalid email format: bad-email",
                "Card last four digits required for card payments"), validator.validate(request));
    }

    @ParameterizedTest
    @ValueSource(strings = {"USD", "eur", "GbP", "JPY", "BRL"})
    void isCurrencySupported_returnsTrueForSupportedCurrencies(String currency) {
        assertTrue(validator.isCurrencySupported(currency));
    }

    @Test
    void isCurrencySupported_returnsFalseForNullOrUnknown() {
        assertFalse(validator.isCurrencySupported(null));
        assertFalse(validator.isCurrencySupported("ZZZ"));
    }

    @Test
    void isValidCardLastFour_acceptsExactlyFourDigits() {
        assertTrue(validator.isValidCardLastFour("0000"));
        assertTrue(validator.isValidCardLastFour("4242"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "123", "12345", "42x2"})
    void isValidCardLastFour_rejectsMalformedValues(String lastFour) {
        assertFalse(validator.isValidCardLastFour(lastFour));
    }

    @Test
    void isValidCardLastFour_rejectsNull() {
        assertFalse(validator.isValidCardLastFour(null));
    }

    private static String repeat(char c, int times) {
        StringBuilder sb = new StringBuilder(times);
        for (int i = 0; i < times; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
