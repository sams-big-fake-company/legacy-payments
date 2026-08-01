package com.bigfake.payments.util;

import com.bigfake.payments.model.dto.PaymentRequest;
import com.bigfake.payments.model.enums.PaymentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PaymentValidatorTest {

    private final PaymentValidator validator = new PaymentValidator();

    private PaymentRequest validRequest() {
        return PaymentRequest.builder()
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
    void validate_validRequest_returnsEmpty() {
        List<String> errors = validator.validate(validRequest());
        assertTrue(errors.isEmpty());
    }

    // --- validate: null request ---

    @Test
    void validate_nullRequest_returnsError() {
        List<String> errors = validator.validate(null);
        assertEquals(1, errors.size());
        assertEquals("Payment request cannot be null", errors.get(0));
    }

    // --- validate: merchant ID ---

    @Test
    void validate_nullMerchantId_returnsError() {
        PaymentRequest request = validRequest();
        request.setMerchantId(null);
        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Merchant ID is required"));
    }

    // --- validate: amount ---

    @Test
    void validate_nullAmount_returnsError() {
        PaymentRequest request = validRequest();
        request.setAmount(null);
        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Amount is required"));
    }

    @Test
    void validate_zeroAmount_returnsError() {
        PaymentRequest request = validRequest();
        request.setAmount(BigDecimal.ZERO);
        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Amount must be positive"));
    }

    @Test
    void validate_negativeAmount_returnsError() {
        PaymentRequest request = validRequest();
        request.setAmount(new BigDecimal("-10.00"));
        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Amount must be positive"));
    }

    @Test
    void validate_amountExceedsMax_returnsError() {
        PaymentRequest request = validRequest();
        request.setAmount(new BigDecimal("1000001.00"));
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("absolute maximum")));
    }

    @Test
    void validate_amountAtMax_noError() {
        PaymentRequest request = validRequest();
        request.setAmount(new BigDecimal("1000000.00"));
        List<String> errors = validator.validate(request);
        assertFalse(errors.stream().anyMatch(e -> e.contains("absolute maximum")));
    }

    @Test
    void validate_amountMoreThan2Decimals_noError() {
        // It only logs a warning, doesn't add an error
        PaymentRequest request = validRequest();
        request.setAmount(new BigDecimal("99.999"));
        List<String> errors = validator.validate(request);
        assertFalse(errors.stream().anyMatch(e -> e.contains("decimal")));
    }

    // --- validate: currency ---

    @Test
    void validate_nullCurrency_returnsError() {
        PaymentRequest request = validRequest();
        request.setCurrency(null);
        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Currency is required"));
    }

    @Test
    void validate_emptyCurrency_returnsError() {
        PaymentRequest request = validRequest();
        request.setCurrency("");
        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Currency is required"));
    }

    @Test
    void validate_invalidLengthCurrency_returnsError() {
        PaymentRequest request = validRequest();
        request.setCurrency("US");
        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Currency must be a 3-letter ISO code"));
    }

    @Test
    void validate_unsupportedCurrency_returnsError() {
        PaymentRequest request = validRequest();
        request.setCurrency("XYZ");
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Unsupported currency")));
    }

    // --- validate: payment type ---

    @Test
    void validate_nullPaymentType_returnsError() {
        PaymentRequest request = validRequest();
        request.setPaymentType(null);
        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Payment type is required"));
    }

    // --- validate: email ---

    @Test
    void validate_invalidEmail_returnsError() {
        PaymentRequest request = validRequest();
        request.setCustomerEmail("not-an-email");
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Invalid email format")));
    }

    @Test
    void validate_validEmail_noError() {
        PaymentRequest request = validRequest();
        request.setCustomerEmail("user@domain.com");
        List<String> errors = validator.validate(request);
        assertFalse(errors.stream().anyMatch(e -> e.contains("email")));
    }

    @Test
    void validate_nullEmail_noError() {
        PaymentRequest request = validRequest();
        request.setCustomerEmail(null);
        List<String> errors = validator.validate(request);
        assertFalse(errors.stream().anyMatch(e -> e.contains("email")));
    }

    @Test
    void validate_emptyEmail_noError() {
        PaymentRequest request = validRequest();
        request.setCustomerEmail("");
        List<String> errors = validator.validate(request);
        assertFalse(errors.stream().anyMatch(e -> e.contains("email")));
    }

    // --- validate: card-specific ---

    @Test
    void validate_creditCard_nullCardLastFour_returnsError() {
        PaymentRequest request = validRequest();
        request.setPaymentType(PaymentType.CREDIT_CARD);
        request.setCardLastFour(null);
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Card last four")));
    }

    @Test
    void validate_creditCard_emptyCardLastFour_returnsError() {
        PaymentRequest request = validRequest();
        request.setPaymentType(PaymentType.CREDIT_CARD);
        request.setCardLastFour("");
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Card last four")));
    }

    @Test
    void validate_creditCard_invalidCardLastFour_returnsError() {
        PaymentRequest request = validRequest();
        request.setPaymentType(PaymentType.CREDIT_CARD);
        request.setCardLastFour("12AB");
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("4 digits")));
    }

    @Test
    void validate_creditCard_tooFewDigits_returnsError() {
        PaymentRequest request = validRequest();
        request.setPaymentType(PaymentType.CREDIT_CARD);
        request.setCardLastFour("123");
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("4 digits")));
    }

    @Test
    void validate_debitCard_nullCardLastFour_returnsError() {
        PaymentRequest request = validRequest();
        request.setPaymentType(PaymentType.DEBIT);
        request.setCardLastFour(null);
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Card last four")));
    }

    @Test
    void validate_wireTransfer_noCardRequired() {
        PaymentRequest request = validRequest();
        request.setPaymentType(PaymentType.WIRE);
        request.setCardLastFour(null);
        List<String> errors = validator.validate(request);
        assertFalse(errors.stream().anyMatch(e -> e.contains("Card")));
    }

    @Test
    void validate_ach_noCardRequired() {
        PaymentRequest request = validRequest();
        request.setPaymentType(PaymentType.ACH);
        request.setCardLastFour(null);
        List<String> errors = validator.validate(request);
        assertFalse(errors.stream().anyMatch(e -> e.contains("Card")));
    }

    // --- validate: description ---

    @Test
    void validate_descriptionTooLong_returnsError() {
        PaymentRequest request = validRequest();
        request.setDescription("A".repeat(501));
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Description")));
    }

    @Test
    void validate_descriptionAtLimit_noError() {
        PaymentRequest request = validRequest();
        request.setDescription("A".repeat(500));
        List<String> errors = validator.validate(request);
        assertFalse(errors.stream().anyMatch(e -> e.contains("Description")));
    }

    // --- validate: multiple errors ---

    @Test
    void validate_multipleErrors_returnsAll() {
        PaymentRequest request = PaymentRequest.builder().build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.size() >= 3);
    }

    // --- isCurrencySupported ---

    @Test
    void isCurrencySupported_supportedCurrency_returnsTrue() {
        assertTrue(validator.isCurrencySupported("USD"));
        assertTrue(validator.isCurrencySupported("EUR"));
        assertTrue(validator.isCurrencySupported("JPY"));
    }

    @Test
    void isCurrencySupported_lowercaseCurrency_returnsTrue() {
        assertTrue(validator.isCurrencySupported("usd"));
    }

    @Test
    void isCurrencySupported_unsupportedCurrency_returnsFalse() {
        assertFalse(validator.isCurrencySupported("XYZ"));
    }

    @Test
    void isCurrencySupported_nullCurrency_returnsFalse() {
        assertFalse(validator.isCurrencySupported(null));
    }

    // --- isValidCardLastFour ---

    @Test
    void isValidCardLastFour_validDigits_returnsTrue() {
        assertTrue(validator.isValidCardLastFour("1234"));
        assertTrue(validator.isValidCardLastFour("0000"));
    }

    @Test
    void isValidCardLastFour_invalidFormat_returnsFalse() {
        assertFalse(validator.isValidCardLastFour("12AB"));
        assertFalse(validator.isValidCardLastFour("123"));
        assertFalse(validator.isValidCardLastFour("12345"));
    }

    @Test
    void isValidCardLastFour_null_returnsFalse() {
        assertFalse(validator.isValidCardLastFour(null));
    }
}
