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
                .amount(new BigDecimal("50.00"))
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
    void validate_nullRequestReturnsSingleError() {
        List<String> errors = validator.validate(null);
        assertEquals(1, errors.size());
        assertEquals("Payment request cannot be null", errors.get(0));
    }

    @Test
    void validate_nullMerchantIdReportsError() {
        PaymentRequest request = validRequest();
        request.setMerchantId(null);
        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Merchant ID is required"));
    }

    @Test
    void validate_nullAmountReportsError() {
        PaymentRequest request = validRequest();
        request.setAmount(null);
        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Amount is required"));
    }

    @Test
    void validate_zeroAmountReportsError() {
        PaymentRequest request = validRequest();
        request.setAmount(BigDecimal.ZERO);
        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Amount must be positive"));
    }

    @Test
    void validate_negativeAmountReportsError() {
        PaymentRequest request = validRequest();
        request.setAmount(new BigDecimal("-10.00"));
        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Amount must be positive"));
    }

    @Test
    void validate_amountExceedsAbsoluteMaxReportsError() {
        PaymentRequest request = validRequest();
        request.setAmount(new BigDecimal("1000001.00"));
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("exceeds absolute maximum")));
    }

    @Test
    void validate_amountAtAbsoluteMaxNoError() {
        PaymentRequest request = validRequest();
        request.setAmount(new BigDecimal("1000000.00"));
        List<String> errors = validator.validate(request);
        assertFalse(errors.stream().anyMatch(e -> e.contains("exceeds absolute maximum")));
    }

    @Test
    void validate_nullCurrencyReportsError() {
        PaymentRequest request = validRequest();
        request.setCurrency(null);
        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Currency is required"));
    }

    @Test
    void validate_emptyCurrencyReportsError() {
        PaymentRequest request = validRequest();
        request.setCurrency("");
        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Currency is required"));
    }

    @Test
    void validate_invalidCurrencyLengthReportsError() {
        PaymentRequest request = validRequest();
        request.setCurrency("US");
        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Currency must be a 3-letter ISO code"));
    }

    @Test
    void validate_unsupportedCurrencyReportsError() {
        PaymentRequest request = validRequest();
        request.setCurrency("XYZ");
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Unsupported currency")));
    }

    @Test
    void validate_nullPaymentTypeReportsError() {
        PaymentRequest request = validRequest();
        request.setPaymentType(null);
        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Payment type is required"));
    }

    @Test
    void validate_invalidEmailReportsError() {
        PaymentRequest request = validRequest();
        request.setCustomerEmail("not-an-email");
        List<String> errors = validator.validate(request);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Invalid email format")));
    }

    @Test
    void validate_validEmailNoError() {
        PaymentRequest request = validRequest();
        request.setCustomerEmail("user@domain.com");
        List<String> errors = validator.validate(request);
        assertFalse(errors.stream().anyMatch(e -> e.contains("email")));
    }

    @Test
    void validate_nullEmailNoError() {
        PaymentRequest request = validRequest();
        request.setCustomerEmail(null);
        List<String> errors = validator.validate(request);
        assertFalse(errors.stream().anyMatch(e -> e.contains("email")));
    }

    @Test
    void validate_emptyEmailNoError() {
        PaymentRequest request = validRequest();
        request.setCustomerEmail("");
        List<String> errors = validator.validate(request);
        assertFalse(errors.stream().anyMatch(e -> e.contains("email")));
    }

    @Test
    void validate_creditCardMissingLastFourReportsError() {
        PaymentRequest request = validRequest();
        request.setPaymentType(PaymentType.CREDIT_CARD);
        request.setCardLastFour(null);
        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Card last four digits required for card payments"));
    }

    @Test
    void validate_creditCardEmptyLastFourReportsError() {
        PaymentRequest request = validRequest();
        request.setPaymentType(PaymentType.CREDIT_CARD);
        request.setCardLastFour("");
        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Card last four digits required for card payments"));
    }

    @Test
    void validate_creditCardInvalidLastFourReportsError() {
        PaymentRequest request = validRequest();
        request.setPaymentType(PaymentType.CREDIT_CARD);
        request.setCardLastFour("12AB");
        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Card last four must be exactly 4 digits"));
    }

    @Test
    void validate_debitCardMissingLastFourReportsError() {
        PaymentRequest request = validRequest();
        request.setPaymentType(PaymentType.DEBIT);
        request.setCardLastFour(null);
        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Card last four digits required for card payments"));
    }

    @Test
    void validate_wireTransferNoCardLastFourRequired() {
        PaymentRequest request = validRequest();
        request.setPaymentType(PaymentType.WIRE);
        request.setCardLastFour(null);
        List<String> errors = validator.validate(request);
        assertFalse(errors.stream().anyMatch(e -> e.contains("Card last four")));
    }

    @Test
    void validate_descriptionTooLongReportsError() {
        PaymentRequest request = validRequest();
        request.setDescription("x".repeat(501));
        List<String> errors = validator.validate(request);
        assertTrue(errors.contains("Description must not exceed 500 characters"));
    }

    @Test
    void validate_descriptionAtMaxLengthNoError() {
        PaymentRequest request = validRequest();
        request.setDescription("x".repeat(500));
        List<String> errors = validator.validate(request);
        assertFalse(errors.stream().anyMatch(e -> e.contains("Description")));
    }

    @Test
    void validate_multipleErrorsReportedTogether() {
        PaymentRequest request = PaymentRequest.builder().build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.size() > 1);
        assertTrue(errors.contains("Merchant ID is required"));
        assertTrue(errors.contains("Amount is required"));
        assertTrue(errors.contains("Currency is required"));
        assertTrue(errors.contains("Payment type is required"));
    }

    @Test
    void isCurrencySupported_supportedReturnsTrue() {
        assertTrue(validator.isCurrencySupported("USD"));
        assertTrue(validator.isCurrencySupported("EUR"));
    }

    @Test
    void isCurrencySupported_unsupportedReturnsFalse() {
        assertFalse(validator.isCurrencySupported("XYZ"));
    }

    @Test
    void isCurrencySupported_nullReturnsFalse() {
        assertFalse(validator.isCurrencySupported(null));
    }

    @Test
    void isCurrencySupported_caseInsensitive() {
        assertTrue(validator.isCurrencySupported("usd"));
    }

    @Test
    void isValidCardLastFour_validReturnTrue() {
        assertTrue(validator.isValidCardLastFour("1234"));
        assertTrue(validator.isValidCardLastFour("0000"));
    }

    @Test
    void isValidCardLastFour_invalidReturnFalse() {
        assertFalse(validator.isValidCardLastFour("12AB"));
        assertFalse(validator.isValidCardLastFour("123"));
        assertFalse(validator.isValidCardLastFour("12345"));
    }

    @Test
    void isValidCardLastFour_nullReturnFalse() {
        assertFalse(validator.isValidCardLastFour(null));
    }
}
