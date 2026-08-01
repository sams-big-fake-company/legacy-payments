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
    void validate_validRequestReturnsNoErrors() {
        List<String> errors = validator.validate(validRequest());
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_nullRequestReturnsError() {
        List<String> errors = validator.validate(null);
        assertEquals(1, errors.size());
        assertEquals("Payment request cannot be null", errors.get(0));
    }

    @Test
    void validate_nullMerchantId() {
        PaymentRequest request = validRequest();
        request.setMerchantId(null);
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Merchant ID")));
    }

    @Test
    void validate_nullAmount() {
        PaymentRequest request = validRequest();
        request.setAmount(null);
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Amount is required")));
    }

    @Test
    void validate_zeroAmount() {
        PaymentRequest request = validRequest();
        request.setAmount(BigDecimal.ZERO);
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Amount must be positive")));
    }

    @Test
    void validate_negativeAmount() {
        PaymentRequest request = validRequest();
        request.setAmount(new BigDecimal("-10.00"));
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Amount must be positive")));
    }

    @Test
    void validate_amountExceedsAbsoluteMax() {
        PaymentRequest request = validRequest();
        request.setAmount(new BigDecimal("1000001.00"));
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("absolute maximum")));
    }

    @Test
    void validate_amountWithMoreThanTwoDecimalPlaces() {
        PaymentRequest request = validRequest();
        request.setAmount(new BigDecimal("99.999"));
        List<String> errors = validator.validate(request);
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_nullCurrency() {
        PaymentRequest request = validRequest();
        request.setCurrency(null);
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Currency is required")));
    }

    @Test
    void validate_emptyCurrency() {
        PaymentRequest request = validRequest();
        request.setCurrency("");
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Currency is required")));
    }

    @Test
    void validate_invalidCurrencyLength() {
        PaymentRequest request = validRequest();
        request.setCurrency("US");
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("3-letter ISO code")));
    }

    @Test
    void validate_unsupportedCurrency() {
        PaymentRequest request = validRequest();
        request.setCurrency("XYZ");
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Unsupported currency")));
    }

    @Test
    void validate_nullPaymentType() {
        PaymentRequest request = validRequest();
        request.setPaymentType(null);
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Payment type is required")));
    }

    @Test
    void validate_invalidEmailFormat() {
        PaymentRequest request = validRequest();
        request.setCustomerEmail("not-an-email");
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Invalid email")));
    }

    @Test
    void validate_validEmailPasses() {
        PaymentRequest request = validRequest();
        request.setCustomerEmail("user@domain.com");
        List<String> errors = validator.validate(request);
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_nullEmailIsOk() {
        PaymentRequest request = validRequest();
        request.setCustomerEmail(null);
        List<String> errors = validator.validate(request);
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_emptyEmailIsOk() {
        PaymentRequest request = validRequest();
        request.setCustomerEmail("");
        List<String> errors = validator.validate(request);
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_creditCardRequiresCardLastFour() {
        PaymentRequest request = validRequest();
        request.setPaymentType(PaymentType.CREDIT_CARD);
        request.setCardLastFour(null);
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Card last four")));
    }

    @Test
    void validate_debitCardRequiresCardLastFour() {
        PaymentRequest request = validRequest();
        request.setPaymentType(PaymentType.DEBIT);
        request.setCardLastFour(null);
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Card last four")));
    }

    @Test
    void validate_invalidCardLastFourFormat() {
        PaymentRequest request = validRequest();
        request.setCardLastFour("abcd");
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("4 digits")));
    }

    @Test
    void validate_emptyCardLastFourForCardPayment() {
        PaymentRequest request = validRequest();
        request.setCardLastFour("");
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Card last four")));
    }

    @Test
    void validate_wireDoesNotRequireCardLastFour() {
        PaymentRequest request = validRequest();
        request.setPaymentType(PaymentType.WIRE);
        request.setCardLastFour(null);
        List<String> errors = validator.validate(request);
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_descriptionTooLong() {
        PaymentRequest request = validRequest();
        request.setDescription("a".repeat(501));
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("500 characters")));
    }

    @Test
    void validate_descriptionAtLimit() {
        PaymentRequest request = validRequest();
        request.setDescription("a".repeat(500));
        List<String> errors = validator.validate(request);
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_multipleErrors() {
        PaymentRequest request = PaymentRequest.builder().build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.size() > 1);
    }

    @Test
    void isCurrencySupported_supported() {
        assertTrue(validator.isCurrencySupported("USD"));
        assertTrue(validator.isCurrencySupported("EUR"));
    }

    @Test
    void isCurrencySupported_lowercase() {
        assertTrue(validator.isCurrencySupported("usd"));
    }

    @Test
    void isCurrencySupported_unsupported() {
        assertFalse(validator.isCurrencySupported("XYZ"));
    }

    @Test
    void isCurrencySupported_null() {
        assertFalse(validator.isCurrencySupported(null));
    }

    @Test
    void isValidCardLastFour_valid() {
        assertTrue(validator.isValidCardLastFour("1234"));
        assertTrue(validator.isValidCardLastFour("0000"));
        assertTrue(validator.isValidCardLastFour("9999"));
    }

    @Test
    void isValidCardLastFour_null() {
        assertFalse(validator.isValidCardLastFour(null));
    }

    @Test
    void isValidCardLastFour_tooShort() {
        assertFalse(validator.isValidCardLastFour("123"));
    }

    @Test
    void isValidCardLastFour_letters() {
        assertFalse(validator.isValidCardLastFour("abcd"));
    }

    @Test
    void isValidCardLastFour_tooLong() {
        assertFalse(validator.isValidCardLastFour("12345"));
    }
}
