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
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .customerEmail("test@example.com")
                .build();
    }

    @Test
    void validate_validRequest_returnsNoErrors() {
        List<String> errors = validator.validate(validRequest());
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_nullRequest_returnsError() {
        List<String> errors = validator.validate(null);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("null"));
    }

    @Test
    void validate_nullMerchantId_returnsError() {
        PaymentRequest request = validRequest();
        request.setMerchantId(null);
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Merchant ID")));
    }

    @Test
    void validate_nullAmount_returnsError() {
        PaymentRequest request = validRequest();
        request.setAmount(null);
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Amount is required")));
    }

    @Test
    void validate_zeroAmount_returnsError() {
        PaymentRequest request = validRequest();
        request.setAmount(BigDecimal.ZERO);
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("positive")));
    }

    @Test
    void validate_negativeAmount_returnsError() {
        PaymentRequest request = validRequest();
        request.setAmount(new BigDecimal("-10.00"));
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("positive")));
    }

    @Test
    void validate_amountExceedsMax_returnsError() {
        PaymentRequest request = validRequest();
        request.setAmount(new BigDecimal("1000001.00"));
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("absolute maximum")));
    }

    @Test
    void validate_amountMoreThan2Decimals_noError() {
        PaymentRequest request = validRequest();
        request.setAmount(new BigDecimal("50.123"));
        List<String> errors = validator.validate(request);
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_nullCurrency_returnsError() {
        PaymentRequest request = validRequest();
        request.setCurrency(null);
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Currency is required")));
    }

    @Test
    void validate_emptyCurrency_returnsError() {
        PaymentRequest request = validRequest();
        request.setCurrency("");
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Currency is required")));
    }

    @Test
    void validate_invalidCurrencyLength_returnsError() {
        PaymentRequest request = validRequest();
        request.setCurrency("US");
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("3-letter")));
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
        assertTrue(errors.stream().anyMatch(e -> e.contains("Payment type")));
    }

    @Test
    void validate_invalidEmail_returnsError() {
        PaymentRequest request = validRequest();
        request.setCustomerEmail("not-an-email");
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("email")));
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
    void validate_creditCard_missingCardLastFour_returnsError() {
        PaymentRequest request = validRequest();
        request.setPaymentType(PaymentType.CREDIT_CARD);
        request.setCardLastFour(null);
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Card last four")));
    }

    @Test
    void validate_debit_missingCardLastFour_returnsError() {
        PaymentRequest request = validRequest();
        request.setPaymentType(PaymentType.DEBIT);
        request.setCardLastFour(null);
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Card last four")));
    }

    @Test
    void validate_creditCard_invalidCardLastFour_returnsError() {
        PaymentRequest request = validRequest();
        request.setCardLastFour("abcd");
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("4 digits")));
    }

    @Test
    void validate_creditCard_emptyCardLastFour_returnsError() {
        PaymentRequest request = validRequest();
        request.setCardLastFour("");
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Card last four")));
    }

    @Test
    void validate_wireTransfer_noCardRequired() {
        PaymentRequest request = validRequest();
        request.setPaymentType(PaymentType.WIRE);
        request.setCardLastFour(null);
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().noneMatch(e -> e.contains("Card")));
    }

    @Test
    void validate_descriptionTooLong_returnsError() {
        PaymentRequest request = validRequest();
        request.setDescription("x".repeat(501));
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("500 characters")));
    }

    @Test
    void validate_descriptionExactly500_noError() {
        PaymentRequest request = validRequest();
        request.setDescription("x".repeat(500));
        List<String> errors = validator.validate(request);
        assertTrue(errors.isEmpty());
    }

    // --- isCurrencySupported ---

    @Test
    void isCurrencySupported_validCurrency_returnsTrue() {
        assertTrue(validator.isCurrencySupported("USD"));
        assertTrue(validator.isCurrencySupported("eur"));
    }

    @Test
    void isCurrencySupported_invalidCurrency_returnsFalse() {
        assertFalse(validator.isCurrencySupported("XYZ"));
    }

    @Test
    void isCurrencySupported_null_returnsFalse() {
        assertFalse(validator.isCurrencySupported(null));
    }

    // --- isValidCardLastFour ---

    @Test
    void isValidCardLastFour_validDigits_returnsTrue() {
        assertTrue(validator.isValidCardLastFour("1234"));
    }

    @Test
    void isValidCardLastFour_letters_returnsFalse() {
        assertFalse(validator.isValidCardLastFour("abcd"));
    }

    @Test
    void isValidCardLastFour_tooFew_returnsFalse() {
        assertFalse(validator.isValidCardLastFour("123"));
    }

    @Test
    void isValidCardLastFour_tooMany_returnsFalse() {
        assertFalse(validator.isValidCardLastFour("12345"));
    }

    @Test
    void isValidCardLastFour_null_returnsFalse() {
        assertFalse(validator.isValidCardLastFour(null));
    }
}
