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

    @BeforeEach
    void setUp() {
        validator = new PaymentValidator();
    }

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

    @Test
    void validate_validRequest_noErrors() {
        List<String> errors = validator.validate(validRequest());
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_nullRequest_returnsError() {
        List<String> errors = validator.validate(null);
        assertEquals(1, errors.size());
        assertEquals("Payment request cannot be null", errors.get(0));
    }

    @Test
    void validate_nullMerchantId_returnsError() {
        PaymentRequest request = validRequest();
        request.setMerchantId(null);
        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Merchant ID is required"));
    }

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
        assertTrue(errors.stream().anyMatch(e -> e.contains("exceeds absolute maximum")));
    }

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
    void validate_invalidCurrencyLength_returnsError() {
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

    @Test
    void validate_nullPaymentType_returnsError() {
        PaymentRequest request = validRequest();
        request.setPaymentType(null);
        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Payment type is required"));
    }

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
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_nullEmail_noError() {
        PaymentRequest request = validRequest();
        request.setCustomerEmail(null);
        List<String> errors = validator.validate(request);
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_emptyEmail_noError() {
        PaymentRequest request = validRequest();
        request.setCustomerEmail("");
        List<String> errors = validator.validate(request);
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_creditCard_missingCardLastFour_returnsError() {
        PaymentRequest request = validRequest();
        request.setPaymentType(PaymentType.CREDIT_CARD);
        request.setCardLastFour(null);
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Card last four digits required")));
    }

    @Test
    void validate_creditCard_emptyCardLastFour_returnsError() {
        PaymentRequest request = validRequest();
        request.setPaymentType(PaymentType.CREDIT_CARD);
        request.setCardLastFour("");
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Card last four digits required")));
    }

    @Test
    void validate_creditCard_invalidCardLastFour_returnsError() {
        PaymentRequest request = validRequest();
        request.setPaymentType(PaymentType.CREDIT_CARD);
        request.setCardLastFour("abcd");
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Card last four must be exactly 4 digits")));
    }

    @Test
    void validate_debitCard_missingCardLastFour_returnsError() {
        PaymentRequest request = validRequest();
        request.setPaymentType(PaymentType.DEBIT);
        request.setCardLastFour(null);
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Card last four digits required")));
    }

    @Test
    void validate_wireTransfer_noCardRequired() {
        PaymentRequest request = validRequest();
        request.setPaymentType(PaymentType.WIRE);
        request.setCardLastFour(null);
        List<String> errors = validator.validate(request);
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_descriptionTooLong_returnsError() {
        PaymentRequest request = validRequest();
        request.setDescription("x".repeat(501));
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Description must not exceed 500 characters")));
    }

    @Test
    void validate_descriptionAtLimit_noError() {
        PaymentRequest request = validRequest();
        request.setDescription("x".repeat(500));
        List<String> errors = validator.validate(request);
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_multipleErrors() {
        PaymentRequest request = PaymentRequest.builder().build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.size() >= 3);
    }

    // --- isCurrencySupported ---

    @Test
    void isCurrencySupported_usd_true() {
        assertTrue(validator.isCurrencySupported("USD"));
    }

    @Test
    void isCurrencySupported_lowercase_true() {
        assertTrue(validator.isCurrencySupported("usd"));
    }

    @Test
    void isCurrencySupported_unknown_false() {
        assertFalse(validator.isCurrencySupported("XYZ"));
    }

    @Test
    void isCurrencySupported_null_false() {
        assertFalse(validator.isCurrencySupported(null));
    }

    // --- isValidCardLastFour ---

    @Test
    void isValidCardLastFour_validDigits_true() {
        assertTrue(validator.isValidCardLastFour("1234"));
    }

    @Test
    void isValidCardLastFour_letters_false() {
        assertFalse(validator.isValidCardLastFour("abcd"));
    }

    @Test
    void isValidCardLastFour_tooShort_false() {
        assertFalse(validator.isValidCardLastFour("123"));
    }

    @Test
    void isValidCardLastFour_tooLong_false() {
        assertFalse(validator.isValidCardLastFour("12345"));
    }

    @Test
    void isValidCardLastFour_null_false() {
        assertFalse(validator.isValidCardLastFour(null));
    }
}
