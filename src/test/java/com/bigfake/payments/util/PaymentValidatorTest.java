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
        assertEquals(List.of(), validator.validate(validRequest()));
    }

    @Test
    void validate_nullRequestReturnsSingleError() {
        List<String> errors = validator.validate(null);
        assertEquals(List.of("Payment request cannot be null"), errors);
    }

    @Test
    void validate_missingMerchantId() {
        PaymentRequest request = validRequest();
        request.setMerchantId(null);
        assertTrue(validator.validate(request).contains("Merchant ID is required"));
    }

    @Test
    void validate_missingAmount() {
        PaymentRequest request = validRequest();
        request.setAmount(null);
        assertTrue(validator.validate(request).contains("Amount is required"));
    }

    @Test
    void validate_zeroAmountIsNotPositive() {
        PaymentRequest request = validRequest();
        request.setAmount(BigDecimal.ZERO);
        assertTrue(validator.validate(request).contains("Amount must be positive"));
    }

    @Test
    void validate_negativeAmountIsNotPositive() {
        PaymentRequest request = validRequest();
        request.setAmount(new BigDecimal("-5.00"));
        assertTrue(validator.validate(request).contains("Amount must be positive"));
    }

    @Test
    void validate_amountAboveAbsoluteMax() {
        PaymentRequest request = validRequest();
        request.setAmount(new BigDecimal("1000000.01"));
        assertTrue(validator.validate(request).contains("Amount exceeds absolute maximum of 1000000.00"));
    }

    @Test
    void validate_amountAtAbsoluteMaxIsValid() {
        PaymentRequest request = validRequest();
        request.setAmount(new BigDecimal("1000000.00"));
        assertEquals(List.of(), validator.validate(request));
    }

    @Test
    void validate_missingCurrency() {
        PaymentRequest request = validRequest();
        request.setCurrency(null);
        assertTrue(validator.validate(request).contains("Currency is required"));
    }

    @Test
    void validate_emptyCurrency() {
        PaymentRequest request = validRequest();
        request.setCurrency("");
        assertTrue(validator.validate(request).contains("Currency is required"));
    }

    @Test
    void validate_currencyMustBeThreeLetters() {
        PaymentRequest request = validRequest();
        request.setCurrency("US");
        assertTrue(validator.validate(request).contains("Currency must be a 3-letter ISO code"));
    }

    @Test
    void validate_unsupportedCurrency() {
        PaymentRequest request = validRequest();
        request.setCurrency("XYZ");
        assertTrue(validator.validate(request).contains("Unsupported currency: XYZ"));
    }

    @Test
    void validate_lowercaseCurrencyIsAccepted() {
        PaymentRequest request = validRequest();
        request.setCurrency("eur");
        assertEquals(List.of(), validator.validate(request));
    }

    @Test
    void validate_missingPaymentType() {
        PaymentRequest request = validRequest();
        request.setPaymentType(null);
        assertTrue(validator.validate(request).contains("Payment type is required"));
    }

    @Test
    void validate_invalidEmailFormat() {
        PaymentRequest request = validRequest();
        request.setCustomerEmail("not-an-email");
        assertTrue(validator.validate(request).contains("Invalid email format: not-an-email"));
    }

    @Test
    void validate_emptyEmailIsAllowed() {
        PaymentRequest request = validRequest();
        request.setCustomerEmail("");
        assertEquals(List.of(), validator.validate(request));
    }

    @Test
    void validate_creditCardRequiresCardLastFour() {
        PaymentRequest request = validRequest();
        request.setCardLastFour(null);
        assertTrue(validator.validate(request).contains("Card last four digits required for card payments"));
    }

    @Test
    void validate_debitRequiresCardLastFour() {
        PaymentRequest request = validRequest();
        request.setPaymentType(PaymentType.DEBIT);
        request.setCardLastFour("");
        assertTrue(validator.validate(request).contains("Card last four digits required for card payments"));
    }

    @Test
    void validate_cardLastFourMustBeFourDigits() {
        PaymentRequest request = validRequest();
        request.setCardLastFour("12a4");
        assertTrue(validator.validate(request).contains("Card last four must be exactly 4 digits"));
    }

    @Test
    void validate_wireDoesNotRequireCard() {
        PaymentRequest request = validRequest();
        request.setPaymentType(PaymentType.WIRE);
        request.setCardLastFour(null);
        assertEquals(List.of(), validator.validate(request));
    }

    @Test
    void validate_descriptionTooLong() {
        PaymentRequest request = validRequest();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 501; i++) {
            sb.append('x');
        }
        request.setDescription(sb.toString());
        assertTrue(validator.validate(request).contains("Description must not exceed 500 characters"));
    }

    @Test
    void validate_collectsMultipleErrors() {
        PaymentRequest request = PaymentRequest.builder().build();
        List<String> errors = validator.validate(request);
        assertTrue(errors.size() >= 4);
    }

    @Test
    void isCurrencySupported_handlesCaseAndNull() {
        assertTrue(validator.isCurrencySupported("usd"));
        assertTrue(validator.isCurrencySupported("BRL"));
        assertFalse(validator.isCurrencySupported("XYZ"));
        assertFalse(validator.isCurrencySupported(null));
    }

    @Test
    void isValidCardLastFour_variousInputs() {
        assertTrue(validator.isValidCardLastFour("4242"));
        assertFalse(validator.isValidCardLastFour("424"));
        assertFalse(validator.isValidCardLastFour("42424"));
        assertFalse(validator.isValidCardLastFour("abcd"));
        assertFalse(validator.isValidCardLastFour(null));
    }
}
