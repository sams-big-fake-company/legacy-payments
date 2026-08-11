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

    private PaymentRequest validCardRequest() {
        return PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("25.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .customerEmail("customer@example.com")
                .build();
    }

    @Test
    void validate_validCardRequest_returnsNoErrors() {
        assertEquals(List.of(), validator.validate(validCardRequest()));
    }

    @Test
    void validate_nullRequest_returnsSingleError() {
        assertEquals(List.of("Payment request cannot be null"), validator.validate(null));
    }

    @Test
    void validate_missingMerchantId_reportsError() {
        PaymentRequest request = validCardRequest();
        request.setMerchantId(null);

        assertTrue(validator.validate(request).contains("Merchant ID is required"));
    }

    @Test
    void validate_missingAmount_reportsError() {
        PaymentRequest request = validCardRequest();
        request.setAmount(null);

        assertTrue(validator.validate(request).contains("Amount is required"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-0.01", "-100.00"})
    void validate_nonPositiveAmount_reportsError(String amount) {
        PaymentRequest request = validCardRequest();
        request.setAmount(new BigDecimal(amount));

        assertTrue(validator.validate(request).contains("Amount must be positive"));
    }

    @Test
    void validate_amountAboveAbsoluteMax_reportsError() {
        PaymentRequest request = validCardRequest();
        request.setAmount(new BigDecimal("1000000.01"));

        assertTrue(validator.validate(request).contains("Amount exceeds absolute maximum of 1000000.00"));
    }

    @Test
    void validate_amountAtAbsoluteMax_isAccepted() {
        PaymentRequest request = validCardRequest();
        request.setAmount(new BigDecimal("1000000.00"));

        assertEquals(List.of(), validator.validate(request));
    }

    @Test
    void validate_amountWithMoreThanTwoDecimals_isStillValid() {
        PaymentRequest request = validCardRequest();
        request.setAmount(new BigDecimal("10.12345"));

        assertEquals(List.of(), validator.validate(request));
    }

    @Test
    void validate_nullCurrency_reportsRequiredError() {
        PaymentRequest request = validCardRequest();
        request.setCurrency(null);

        assertTrue(validator.validate(request).contains("Currency is required"));
    }

    @Test
    void validate_emptyCurrency_reportsRequiredError() {
        PaymentRequest request = validCardRequest();
        request.setCurrency("");

        assertTrue(validator.validate(request).contains("Currency is required"));
    }

    @Test
    void validate_currencyWithWrongLength_reportsIsoAndSupportErrors() {
        PaymentRequest request = validCardRequest();
        request.setCurrency("USDD");

        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Currency must be a 3-letter ISO code"));
        assertTrue(errors.contains("Unsupported currency: USDD"));
    }

    @Test
    void validate_unsupportedThreeLetterCurrency_reportsError() {
        PaymentRequest request = validCardRequest();
        request.setCurrency("ZZZ");

        List<String> errors = validator.validate(request);
        assertEquals(List.of("Unsupported currency: ZZZ"), errors);
    }

    @Test
    void validate_lowercaseSupportedCurrency_isAccepted() {
        PaymentRequest request = validCardRequest();
        request.setCurrency("eur");

        assertEquals(List.of(), validator.validate(request));
    }

    @Test
    void validate_missingPaymentType_reportsError() {
        PaymentRequest request = validCardRequest();
        request.setPaymentType(null);

        assertEquals(List.of("Payment type is required"), validator.validate(request));
    }

    @Test
    void validate_invalidEmail_reportsError() {
        PaymentRequest request = validCardRequest();
        request.setCustomerEmail("not-an-email");

        assertTrue(validator.validate(request).contains("Invalid email format: not-an-email"));
    }

    @Test
    void validate_emptyEmail_isIgnored() {
        PaymentRequest request = validCardRequest();
        request.setCustomerEmail("");

        assertEquals(List.of(), validator.validate(request));
    }

    @Test
    void validate_nullEmail_isIgnored() {
        PaymentRequest request = validCardRequest();
        request.setCustomerEmail(null);

        assertEquals(List.of(), validator.validate(request));
    }

    @ParameterizedTest
    @CsvSource({"CREDIT_CARD", "DEBIT"})
    void validate_cardPaymentWithoutLastFour_reportsError(PaymentType type) {
        PaymentRequest request = validCardRequest();
        request.setPaymentType(type);
        request.setCardLastFour(null);

        assertEquals(List.of("Card last four digits required for card payments"), validator.validate(request));
    }

    @ParameterizedTest
    @CsvSource({"CREDIT_CARD", "DEBIT"})
    void validate_cardPaymentWithEmptyLastFour_reportsError(PaymentType type) {
        PaymentRequest request = validCardRequest();
        request.setPaymentType(type);
        request.setCardLastFour("");

        assertEquals(List.of("Card last four digits required for card payments"), validator.validate(request));
    }

    @ParameterizedTest
    @ValueSource(strings = {"12", "12345", "abcd", "12a4"})
    void validate_cardPaymentWithMalformedLastFour_reportsError(String lastFour) {
        PaymentRequest request = validCardRequest();
        request.setCardLastFour(lastFour);

        assertEquals(List.of("Card last four must be exactly 4 digits"), validator.validate(request));
    }

    @Test
    void validate_wirePaymentWithoutCardDetails_isAccepted() {
        PaymentRequest request = validCardRequest();
        request.setPaymentType(PaymentType.WIRE);
        request.setCardLastFour(null);

        assertEquals(List.of(), validator.validate(request));
    }

    @Test
    void validate_descriptionTooLong_reportsError() {
        PaymentRequest request = validCardRequest();
        request.setDescription(repeat("x", 501));

        assertEquals(List.of("Description must not exceed 500 characters"), validator.validate(request));
    }

    @Test
    void validate_descriptionAtMaxLength_isAccepted() {
        PaymentRequest request = validCardRequest();
        request.setDescription(repeat("x", 500));

        assertEquals(List.of(), validator.validate(request));
    }

    @Test
    void validate_multipleProblems_reportsAllErrors() {
        PaymentRequest request = PaymentRequest.builder()
                .amount(new BigDecimal("-5.00"))
                .currency("ZZZ")
                .paymentType(PaymentType.CREDIT_CARD)
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

    @ParameterizedTest
    @ValueSource(strings = {"USD", "eur", "GbP", "BRL"})
    void isCurrencySupported_supportedCurrencies_returnTrue(String currency) {
        assertTrue(validator.isCurrencySupported(currency));
    }

    @Test
    void isCurrencySupported_unknownOrNullCurrency_returnsFalse() {
        assertFalse(validator.isCurrencySupported("ZZZ"));
        assertFalse(validator.isCurrencySupported(null));
    }

    @Test
    void isValidCardLastFour_checksFourDigitFormat() {
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
