package com.bigfake.payments.util;

import com.bigfake.payments.model.dto.PaymentRequest;
import com.bigfake.payments.model.enums.PaymentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PaymentValidatorTest {

    private PaymentValidator validator;
    private PaymentRequest validRequest;

    @BeforeEach
    void setUp() {
        validator = new PaymentValidator();
        validRequest = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("99.99"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .customerEmail("test@example.com")
                .build();
    }

    // --- validate: happy path ---

    @Test
    void validate_validRequest_returnsEmptyList() {
        List<String> errors = validator.validate(validRequest);
        assertTrue(errors.isEmpty());
    }

    // --- validate: null request ---

    @Test
    void validate_nullRequest_returnsError() {
        List<String> errors = validator.validate(null);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("null"));
    }

    // --- validate: merchantId ---

    @Test
    void validate_nullMerchantId_returnsError() {
        validRequest.setMerchantId(null);
        List<String> errors = validator.validate(validRequest);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Merchant ID")));
    }

    // --- validate: amount ---

    @Test
    void validate_nullAmount_returnsError() {
        validRequest.setAmount(null);
        List<String> errors = validator.validate(validRequest);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Amount is required")));
    }

    @Test
    void validate_zeroAmount_returnsError() {
        validRequest.setAmount(BigDecimal.ZERO);
        List<String> errors = validator.validate(validRequest);
        assertTrue(errors.stream().anyMatch(e -> e.contains("positive")));
    }

    @Test
    void validate_negativeAmount_returnsError() {
        validRequest.setAmount(new BigDecimal("-10.00"));
        List<String> errors = validator.validate(validRequest);
        assertTrue(errors.stream().anyMatch(e -> e.contains("positive")));
    }

    @Test
    void validate_exceedsAbsoluteMax_returnsError() {
        validRequest.setAmount(new BigDecimal("1000001.00"));
        List<String> errors = validator.validate(validRequest);
        assertTrue(errors.stream().anyMatch(e -> e.contains("absolute maximum")));
    }

    @Test
    void validate_amountAtAbsoluteMax_noError() {
        validRequest.setAmount(new BigDecimal("1000000.00"));
        List<String> errors = validator.validate(validRequest);
        assertTrue(errors.stream().noneMatch(e -> e.contains("absolute maximum")));
    }

    @Test
    void validate_amountWithMoreThanTwoDecimals_noError() {
        validRequest.setAmount(new BigDecimal("99.999"));
        List<String> errors = validator.validate(validRequest);
        assertTrue(errors.isEmpty());
    }

    // --- validate: currency ---

    @Test
    void validate_nullCurrency_returnsError() {
        validRequest.setCurrency(null);
        List<String> errors = validator.validate(validRequest);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Currency is required")));
    }

    @Test
    void validate_emptyCurrency_returnsError() {
        validRequest.setCurrency("");
        List<String> errors = validator.validate(validRequest);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Currency is required")));
    }

    @Test
    void validate_invalidCurrencyLength_returnsError() {
        validRequest.setCurrency("US");
        List<String> errors = validator.validate(validRequest);
        assertTrue(errors.stream().anyMatch(e -> e.contains("3-letter ISO")));
    }

    @Test
    void validate_unsupportedCurrency_returnsError() {
        validRequest.setCurrency("XYZ");
        List<String> errors = validator.validate(validRequest);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Unsupported currency")));
    }

    // --- validate: paymentType ---

    @Test
    void validate_nullPaymentType_returnsError() {
        validRequest.setPaymentType(null);
        validRequest.setCardLastFour(null);
        List<String> errors = validator.validate(validRequest);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Payment type")));
    }

    // --- validate: email ---

    @Test
    void validate_invalidEmail_returnsError() {
        validRequest.setCustomerEmail("not-an-email");
        List<String> errors = validator.validate(validRequest);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Invalid email")));
    }

    @Test
    void validate_validEmail_noError() {
        validRequest.setCustomerEmail("user@example.com");
        List<String> errors = validator.validate(validRequest);
        assertTrue(errors.stream().noneMatch(e -> e.contains("email")));
    }

    @Test
    void validate_nullEmail_noError() {
        validRequest.setCustomerEmail(null);
        List<String> errors = validator.validate(validRequest);
        assertTrue(errors.stream().noneMatch(e -> e.contains("email")));
    }

    // --- validate: card info for card payments ---

    @Test
    void validate_creditCardMissingLastFour_returnsError() {
        validRequest.setPaymentType(PaymentType.CREDIT_CARD);
        validRequest.setCardLastFour(null);
        List<String> errors = validator.validate(validRequest);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Card last four")));
    }

    @Test
    void validate_debitMissingLastFour_returnsError() {
        validRequest.setPaymentType(PaymentType.DEBIT);
        validRequest.setCardLastFour(null);
        List<String> errors = validator.validate(validRequest);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Card last four")));
    }

    @Test
    void validate_cardLastFourInvalidFormat_returnsError() {
        validRequest.setCardLastFour("ABCD");
        List<String> errors = validator.validate(validRequest);
        assertTrue(errors.stream().anyMatch(e -> e.contains("4 digits")));
    }

    @Test
    void validate_wireTransferNoCardRequired_noCardError() {
        validRequest.setPaymentType(PaymentType.WIRE);
        validRequest.setCardLastFour(null);
        List<String> errors = validator.validate(validRequest);
        assertTrue(errors.stream().noneMatch(e -> e.contains("Card")));
    }

    // --- validate: description ---

    @Test
    void validate_descriptionTooLong_returnsError() {
        validRequest.setDescription("x".repeat(501));
        List<String> errors = validator.validate(validRequest);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Description")));
    }

    @Test
    void validate_descriptionAtLimit_noError() {
        validRequest.setDescription("x".repeat(500));
        List<String> errors = validator.validate(validRequest);
        assertTrue(errors.stream().noneMatch(e -> e.contains("Description")));
    }

    // --- validate: multiple errors ---

    @Test
    void validate_multipleErrors_returnsAll() {
        PaymentRequest badRequest = PaymentRequest.builder()
                .amount(new BigDecimal("-1.00"))
                .currency("XXXX")
                .build();
        List<String> errors = validator.validate(badRequest);
        assertTrue(errors.size() >= 3);
    }

    // --- isCurrencySupported ---

    @Test
    void isCurrencySupported_supportedCurrency_returnsTrue() {
        assertTrue(validator.isCurrencySupported("USD"));
    }

    @Test
    void isCurrencySupported_unsupportedCurrency_returnsFalse() {
        assertFalse(validator.isCurrencySupported("XYZ"));
    }

    @Test
    void isCurrencySupported_nullCurrency_returnsFalse() {
        assertFalse(validator.isCurrencySupported(null));
    }

    @Test
    void isCurrencySupported_lowercaseCurrency_returnsTrue() {
        assertTrue(validator.isCurrencySupported("usd"));
    }

    // --- isValidCardLastFour ---

    @Test
    void isValidCardLastFour_validFourDigits_returnsTrue() {
        assertTrue(validator.isValidCardLastFour("1234"));
    }

    @Test
    void isValidCardLastFour_null_returnsFalse() {
        assertFalse(validator.isValidCardLastFour(null));
    }

    @Test
    void isValidCardLastFour_letters_returnsFalse() {
        assertFalse(validator.isValidCardLastFour("abcd"));
    }

    @Test
    void isValidCardLastFour_threeDigits_returnsFalse() {
        assertFalse(validator.isValidCardLastFour("123"));
    }

    @Test
    void isValidCardLastFour_fiveDigits_returnsFalse() {
        assertFalse(validator.isValidCardLastFour("12345"));
    }
}
