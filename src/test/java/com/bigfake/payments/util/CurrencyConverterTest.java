package com.bigfake.payments.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

class CurrencyConverterTest {

    private CurrencyConverter converter;

    @BeforeEach
    void setUp() {
        converter = new CurrencyConverter();
    }

    @Test
    void convertToUsd_usdReturnsOriginalAmount() {
        BigDecimal amount = new BigDecimal("100.00");
        BigDecimal result = converter.convertToUsd(amount, "USD");
        assertEquals(amount, result);
    }

    @Test
    void convertToUsd_eurConvertsCorrectly() {
        BigDecimal amount = new BigDecimal("100.00");
        BigDecimal result = converter.convertToUsd(amount, "EUR");
        BigDecimal expected = new BigDecimal("100.00")
                .multiply(new BigDecimal("1.0875"))
                .setScale(4, RoundingMode.HALF_UP);
        assertEquals(expected, result);
    }

    @Test
    void convertToUsd_gbpConvertsCorrectly() {
        BigDecimal amount = new BigDecimal("50.00");
        BigDecimal result = converter.convertToUsd(amount, "GBP");
        BigDecimal expected = new BigDecimal("50.00")
                .multiply(new BigDecimal("1.2650"))
                .setScale(4, RoundingMode.HALF_UP);
        assertEquals(expected, result);
    }

    @Test
    void convertToUsd_lowercaseCurrencyCode() {
        BigDecimal amount = new BigDecimal("100.00");
        BigDecimal result = converter.convertToUsd(amount, "eur");
        assertNotNull(result);
    }

    @Test
    void convertToUsd_unsupportedCurrencyReturnsNull() {
        BigDecimal amount = new BigDecimal("100.00");
        BigDecimal result = converter.convertToUsd(amount, "XYZ");
        assertNull(result);
    }

    @Test
    void convertToUsd_nullAmountReturnsNull() {
        assertNull(converter.convertToUsd(null, "USD"));
    }

    @Test
    void convertToUsd_nullCurrencyReturnsNull() {
        assertNull(converter.convertToUsd(new BigDecimal("100.00"), null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CAD", "AUD", "JPY", "CHF", "CNY", "INR", "BRL"})
    void convertToUsd_allSupportedCurrencies(String currency) {
        BigDecimal amount = new BigDecimal("1000.00");
        BigDecimal result = converter.convertToUsd(amount, currency);
        assertNotNull(result);
        assertTrue(result.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void convertFromUsd_usdReturnsOriginalAmount() {
        BigDecimal amount = new BigDecimal("100.00");
        BigDecimal result = converter.convertFromUsd(amount, "USD");
        assertEquals(amount, result);
    }

    @Test
    void convertFromUsd_eurConvertsCorrectly() {
        BigDecimal amount = new BigDecimal("108.75");
        BigDecimal result = converter.convertFromUsd(amount, "EUR");
        BigDecimal expected = new BigDecimal("108.75")
                .divide(new BigDecimal("1.0875"), 4, RoundingMode.HALF_UP);
        assertEquals(expected, result);
    }

    @Test
    void convertFromUsd_unsupportedCurrencyReturnsNull() {
        assertNull(converter.convertFromUsd(new BigDecimal("100.00"), "XYZ"));
    }

    @Test
    void convertFromUsd_nullAmountReturnsNull() {
        assertNull(converter.convertFromUsd(null, "EUR"));
    }

    @Test
    void convertFromUsd_nullCurrencyReturnsNull() {
        assertNull(converter.convertFromUsd(new BigDecimal("100.00"), null));
    }

    @Test
    void convertFromUsd_lowercaseCurrencyCode() {
        BigDecimal result = converter.convertFromUsd(new BigDecimal("100.00"), "gbp");
        assertNotNull(result);
    }

    @Test
    void isSupported_supportedCurrencyReturnsTrue() {
        assertTrue(converter.isSupported("USD"));
        assertTrue(converter.isSupported("EUR"));
        assertTrue(converter.isSupported("GBP"));
    }

    @Test
    void isSupported_lowercaseReturnsTrue() {
        assertTrue(converter.isSupported("usd"));
    }

    @Test
    void isSupported_unsupportedCurrencyReturnsFalse() {
        assertFalse(converter.isSupported("XYZ"));
    }

    @Test
    void isSupported_nullReturnsFalse() {
        assertFalse(converter.isSupported(null));
    }

    @Test
    void convertToUsd_roundTripConversion() {
        BigDecimal original = new BigDecimal("100.0000");
        BigDecimal inUsd = converter.convertToUsd(original, "EUR");
        BigDecimal backToEur = converter.convertFromUsd(inUsd, "EUR");
        assertEquals(0, original.compareTo(backToEur));
    }
}
