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

    @Test
    void validate_validRequest_noErrors() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("99.99"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .customerEmail("test@example.com")
                .build();

        List<String> errors = validator.validate(request);

        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_nullRequest_returnsError() {
        List<String> errors = validator.validate(null);

        assertEquals(1, errors.size());
        assertEquals("Payment request cannot be null", errors.get(0));
    }

    @Test
    void validate_nullMerchantId() {
        PaymentRequest request = PaymentRequest.builder()
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .paymentType(PaymentType.WIRE)
                .build();

        List<String> errors = validator.validate(request);

        assertTrue(errors.contains("Merchant ID is required"));
    }

    @Test
    void validate_nullAmount() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .build();

        List<String> errors = validator.validate(request);

        assertTrue(errors.contains("Amount is required"));
    }

    @Test
    void validate_negativeAmount() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("-5.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .build();

        List<String> errors = validator.validate(request);

        assertTrue(errors.contains("Amount must be positive"));
    }

    @Test
    void validate_zeroAmount() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(BigDecimal.ZERO)
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .build();

        List<String> errors = validator.validate(request);

        assertTrue(errors.contains("Amount must be positive"));
    }

    @Test
    void validate_amountExceedsAbsoluteMax() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("1000001.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .build();

        List<String> errors = validator.validate(request);

        assertTrue(errors.stream().anyMatch(e -> e.contains("exceeds absolute maximum")));
    }

    @Test
    void validate_nullCurrency() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("10.00"))
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .build();

        List<String> errors = validator.validate(request);

        assertTrue(errors.contains("Currency is required"));
    }

    @Test
    void validate_emptyCurrency() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("10.00"))
                .currency("")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .build();

        List<String> errors = validator.validate(request);

        assertTrue(errors.contains("Currency is required"));
    }

    @Test
    void validate_invalidCurrencyLength() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("10.00"))
                .currency("US")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .build();

        List<String> errors = validator.validate(request);

        assertTrue(errors.contains("Currency must be a 3-letter ISO code"));
    }

    @Test
    void validate_unsupportedCurrency() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("10.00"))
                .currency("XYZ")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("4242")
                .build();

        List<String> errors = validator.validate(request);

        assertTrue(errors.stream().anyMatch(e -> e.contains("Unsupported currency")));
    }

    @Test
    void validate_nullPaymentType() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .build();

        List<String> errors = validator.validate(request);

        assertTrue(errors.contains("Payment type is required"));
    }

    @Test
    void validate_invalidEmail() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .paymentType(PaymentType.WIRE)
                .customerEmail("not-an-email")
                .build();

        List<String> errors = validator.validate(request);

        assertTrue(errors.stream().anyMatch(e -> e.contains("Invalid email format")));
    }

    @Test
    void validate_validEmail_noError() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .paymentType(PaymentType.WIRE)
                .customerEmail("valid@test.com")
                .build();

        List<String> errors = validator.validate(request);

        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_creditCard_missingCardLastFour() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .build();

        List<String> errors = validator.validate(request);

        assertTrue(errors.stream().anyMatch(e -> e.contains("Card last four digits required")));
    }

    @Test
    void validate_creditCard_invalidCardLastFour() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .paymentType(PaymentType.CREDIT_CARD)
                .cardLastFour("abcd")
                .build();

        List<String> errors = validator.validate(request);

        assertTrue(errors.stream().anyMatch(e -> e.contains("Card last four must be exactly 4 digits")));
    }

    @Test
    void validate_debit_missingCardLastFour() {
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .paymentType(PaymentType.DEBIT)
                .build();

        List<String> errors = validator.validate(request);

        assertTrue(errors.stream().anyMatch(e -> e.contains("Card last four digits required")));
    }

    @Test
    void validate_descriptionTooLong() {
        String longDesc = "A".repeat(501);
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .paymentType(PaymentType.WIRE)
                .description(longDesc)
                .build();

        List<String> errors = validator.validate(request);

        assertTrue(errors.stream().anyMatch(e -> e.contains("Description must not exceed 500")));
    }

    @Test
    void validate_descriptionExactly500_noError() {
        String desc = "A".repeat(500);
        PaymentRequest request = PaymentRequest.builder()
                .merchantId(1L)
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .paymentType(PaymentType.WIRE)
                .description(desc)
                .build();

        List<String> errors = validator.validate(request);

        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_multipleErrors() {
        PaymentRequest request = PaymentRequest.builder()
                .amount(new BigDecimal("-1.00"))
                .currency("XXXX")
                .build();

        List<String> errors = validator.validate(request);

        assertTrue(errors.size() >= 3);
    }

    @Test
    void isCurrencySupported_supported() {
        assertTrue(validator.isCurrencySupported("USD"));
        assertTrue(validator.isCurrencySupported("EUR"));
    }

    @Test
    void isCurrencySupported_unsupported() {
        assertFalse(validator.isCurrencySupported("XYZ"));
    }

    @Test
    void isCurrencySupported_null() {
        assertFalse(validator.isCurrencySupported(null));
    }
}
