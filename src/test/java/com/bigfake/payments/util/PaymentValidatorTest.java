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

    // ---- validate: happy path ----

    @Test
    void validate_validRequest_returnsEmptyList() {
        List<String> errors = validator.validate(validRequest());
        assertTrue(errors.isEmpty());
    }

    // ---- validate: null request ----

    @Test
    void validate_nullRequest_returnsError() {
        List<String> errors = validator.validate(null);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("null"));
    }

    // ---- validate: merchantId ----

    @Test
    void validate_nullMerchantId_returnsError() {
        PaymentRequest req = validRequest();
        req.setMerchantId(null);
        List<String> errors = validator.validate(req);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Merchant ID")));
    }

    // ---- validate: amount ----

    @Test
    void validate_nullAmount_returnsError() {
        PaymentRequest req = validRequest();
        req.setAmount(null);
        List<String> errors = validator.validate(req);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Amount is required")));
    }

    @Test
    void validate_zeroAmount_returnsError() {
        PaymentRequest req = validRequest();
        req.setAmount(BigDecimal.ZERO);
        List<String> errors = validator.validate(req);
        assertTrue(errors.stream().anyMatch(e -> e.contains("positive")));
    }

    @Test
    void validate_negativeAmount_returnsError() {
        PaymentRequest req = validRequest();
        req.setAmount(new BigDecimal("-10.00"));
        List<String> errors = validator.validate(req);
        assertTrue(errors.stream().anyMatch(e -> e.contains("positive")));
    }

    @Test
    void validate_amountExceedsAbsoluteMax_returnsError() {
        PaymentRequest req = validRequest();
        req.setAmount(new BigDecimal("1000001.00"));
        List<String> errors = validator.validate(req);
        assertTrue(errors.stream().anyMatch(e -> e.contains("absolute maximum")));
    }

    @Test
    void validate_amountWithMoreThan2Decimals_noError() {
        PaymentRequest req = validRequest();
        req.setAmount(new BigDecimal("99.999"));
        List<String> errors = validator.validate(req);
        assertTrue(errors.isEmpty());
    }

    // ---- validate: currency ----

    @Test
    void validate_nullCurrency_returnsError() {
        PaymentRequest req = validRequest();
        req.setCurrency(null);
        List<String> errors = validator.validate(req);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Currency is required")));
    }

    @Test
    void validate_emptyCurrency_returnsError() {
        PaymentRequest req = validRequest();
        req.setCurrency("");
        List<String> errors = validator.validate(req);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Currency is required")));
    }

    @Test
    void validate_invalidCurrencyLength_returnsError() {
        PaymentRequest req = validRequest();
        req.setCurrency("US");
        List<String> errors = validator.validate(req);
        assertTrue(errors.stream().anyMatch(e -> e.contains("3-letter")));
    }

    @Test
    void validate_unsupportedCurrency_returnsError() {
        PaymentRequest req = validRequest();
        req.setCurrency("XYZ");
        List<String> errors = validator.validate(req);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Unsupported currency")));
    }

    // ---- validate: paymentType ----

    @Test
    void validate_nullPaymentType_returnsError() {
        PaymentRequest req = validRequest();
        req.setPaymentType(null);
        List<String> errors = validator.validate(req);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Payment type")));
    }

    // ---- validate: customerEmail ----

    @Test
    void validate_invalidEmail_returnsError() {
        PaymentRequest req = validRequest();
        req.setCustomerEmail("not-an-email");
        List<String> errors = validator.validate(req);
        assertTrue(errors.stream().anyMatch(e -> e.contains("email")));
    }

    @Test
    void validate_validEmail_noError() {
        PaymentRequest req = validRequest();
        req.setCustomerEmail("user@domain.com");
        List<String> errors = validator.validate(req);
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_nullEmail_noError() {
        PaymentRequest req = validRequest();
        req.setCustomerEmail(null);
        List<String> errors = validator.validate(req);
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_emptyEmail_noError() {
        PaymentRequest req = validRequest();
        req.setCustomerEmail("");
        List<String> errors = validator.validate(req);
        assertTrue(errors.isEmpty());
    }

    // ---- validate: card-specific ----

    @Test
    void validate_creditCardMissingLastFour_returnsError() {
        PaymentRequest req = validRequest();
        req.setPaymentType(PaymentType.CREDIT_CARD);
        req.setCardLastFour(null);
        List<String> errors = validator.validate(req);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Card last four")));
    }

    @Test
    void validate_creditCardEmptyLastFour_returnsError() {
        PaymentRequest req = validRequest();
        req.setPaymentType(PaymentType.CREDIT_CARD);
        req.setCardLastFour("");
        List<String> errors = validator.validate(req);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Card last four")));
    }

    @Test
    void validate_creditCardInvalidLastFour_returnsError() {
        PaymentRequest req = validRequest();
        req.setPaymentType(PaymentType.CREDIT_CARD);
        req.setCardLastFour("abcd");
        List<String> errors = validator.validate(req);
        assertTrue(errors.stream().anyMatch(e -> e.contains("4 digits")));
    }

    @Test
    void validate_debitMissingLastFour_returnsError() {
        PaymentRequest req = validRequest();
        req.setPaymentType(PaymentType.DEBIT);
        req.setCardLastFour(null);
        List<String> errors = validator.validate(req);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Card last four")));
    }

    @Test
    void validate_wireTransfer_noCardRequired() {
        PaymentRequest req = validRequest();
        req.setPaymentType(PaymentType.WIRE);
        req.setCardLastFour(null);
        List<String> errors = validator.validate(req);
        assertTrue(errors.isEmpty());
    }

    // ---- validate: description length ----

    @Test
    void validate_descriptionTooLong_returnsError() {
        PaymentRequest req = validRequest();
        req.setDescription("x".repeat(501));
        List<String> errors = validator.validate(req);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Description")));
    }

    @Test
    void validate_descriptionExactMax_noError() {
        PaymentRequest req = validRequest();
        req.setDescription("x".repeat(500));
        List<String> errors = validator.validate(req);
        assertTrue(errors.isEmpty());
    }

    // ---- isCurrencySupported ----

    @Test
    void isCurrencySupported_supportedCurrency_returnsTrue() {
        assertTrue(validator.isCurrencySupported("USD"));
        assertTrue(validator.isCurrencySupported("EUR"));
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

    // ---- isValidCardLastFour ----

    @Test
    void isValidCardLastFour_validDigits_returnsTrue() {
        assertTrue(validator.isValidCardLastFour("4242"));
        assertTrue(validator.isValidCardLastFour("0000"));
        assertTrue(validator.isValidCardLastFour("9999"));
    }

    @Test
    void isValidCardLastFour_invalidFormat_returnsFalse() {
        assertFalse(validator.isValidCardLastFour("abcd"));
        assertFalse(validator.isValidCardLastFour("123"));
        assertFalse(validator.isValidCardLastFour("12345"));
    }

    @Test
    void isValidCardLastFour_null_returnsFalse() {
        assertFalse(validator.isValidCardLastFour(null));
    }

    // ---- validate: multiple errors ----

    @Test
    void validate_multipleErrors_returnsAllErrors() {
        PaymentRequest req = PaymentRequest.builder().build();
        List<String> errors = validator.validate(req);
        assertTrue(errors.size() >= 3);
    }
}
