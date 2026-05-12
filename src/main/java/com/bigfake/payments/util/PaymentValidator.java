package com.bigfake.payments.util;

import com.bigfake.payments.model.dto.PaymentRequest;
import com.bigfake.payments.model.enums.PaymentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Utility class for payment validation.
 *
 * NOTE: This class has accumulated a lot of validation rules over the years.
 * Many of them are duplicated from bean validation annotations.
 * TODO: PAY-3900 - Consolidate validation logic, remove duplicates
 * TODO: PAY-3901 - Use javax.validation properly instead of manual checks
 *
 * @deprecated Most of this should be handled by Bean Validation annotations.
 * Kept for backward compatibility and some edge cases not covered by annotations.
 */
@Deprecated
@Component
public class PaymentValidator {

    private static final Logger log = LoggerFactory.getLogger(PaymentValidator.class);

    // TODO: PAY-3910 - These should be configurable
    private static final List<String> SUPPORTED_CURRENCIES = Arrays.asList(
            "USD", "EUR", "GBP", "CAD", "AUD", "JPY", "CHF", "CNY", "INR", "BRL"
    );

    private static final List<String> BLOCKED_COUNTRIES = Arrays.asList(
            "XX", "YY" // Placeholder - real list is in compliance database
    );

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$"
    );

    private static final BigDecimal ABSOLUTE_MAX = new BigDecimal("1000000.00");

    /**
     * Validates a payment request. Returns list of validation errors.
     * Empty list means valid.
     *
     * @param request the payment request to validate
     * @return list of validation error messages (empty if valid)
     */
    public List<String> validate(PaymentRequest request) {
        List<String> errors = new ArrayList<>();

        // Null checks (should be handled by @NotNull but we double-check)
        if (request == null) {
            errors.add("Payment request cannot be null");
            return errors;
        }

        if (request.getMerchantId() == null) {
            errors.add("Merchant ID is required");
        }

        if (request.getAmount() == null) {
            errors.add("Amount is required");
        } else {
            if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                errors.add("Amount must be positive");
            }
            if (request.getAmount().compareTo(ABSOLUTE_MAX) > 0) {
                errors.add("Amount exceeds absolute maximum of " + ABSOLUTE_MAX);
            }
            // Check decimal places
            if (request.getAmount().scale() > 2) {
                // Some currencies support more decimals but we'll flag it
                log.warn("Payment amount has more than 2 decimal places: {}", request.getAmount());
            }
        }

        if (request.getCurrency() == null || request.getCurrency().isEmpty()) {
            errors.add("Currency is required");
        } else {
            if (request.getCurrency().length() != 3) {
                errors.add("Currency must be a 3-letter ISO code");
            }
            if (!SUPPORTED_CURRENCIES.contains(request.getCurrency().toUpperCase())) {
                errors.add("Unsupported currency: " + request.getCurrency());
            }
        }

        if (request.getPaymentType() == null) {
            errors.add("Payment type is required");
        }

        // Email validation (duplicates @Email annotation but whatever)
        if (request.getCustomerEmail() != null && !request.getCustomerEmail().isEmpty()) {
            if (!EMAIL_PATTERN.matcher(request.getCustomerEmail()).matches()) {
                errors.add("Invalid email format: " + request.getCustomerEmail());
            }
        }

        // Card-specific validation
        if (request.getPaymentType() == PaymentType.CREDIT_CARD || request.getPaymentType() == PaymentType.DEBIT) {
            if (request.getCardLastFour() == null || request.getCardLastFour().isEmpty()) {
                errors.add("Card last four digits required for card payments");
            } else if (!request.getCardLastFour().matches("\\d{4}")) {
                errors.add("Card last four must be exactly 4 digits");
            }
        }

        // Description length check (also done by @Size but legacy code...)
        if (request.getDescription() != null && request.getDescription().length() > 500) {
            errors.add("Description must not exceed 500 characters");
        }

        if (!errors.isEmpty()) {
            log.warn("Payment validation failed with {} errors for merchant {}",
                    errors.size(), request.getMerchantId());
        }

        return errors;
    }

    /**
     * Quick check if a currency is supported.
     * @deprecated Use CurrencyService.isSupported() instead
     */
    @Deprecated
    public boolean isCurrencySupported(String currency) {
        return currency != null && SUPPORTED_CURRENCIES.contains(currency.toUpperCase());
    }

    /**
     * Validates card number format (last 4 only - we don't store full PAN).
     */
    public boolean isValidCardLastFour(String lastFour) {
        return lastFour != null && lastFour.matches("\\d{4}");
    }
}
