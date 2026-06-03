package com.bigfake.payments.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CurrencyConverterTest {

    private final CurrencyConverter converter = new CurrencyConverter();

    // --- convertToUsd ---

    @Test
    void convertToUsd_usd_returnsSameAmount() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("100.00"), "USD");
        assertEquals(new BigDecimal("100.00"), result);
    }

    @Test
    void convertToUsd_eur_convertsCorrectly() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("100.00"), "EUR");
        assertNotNull(result);
        assertEquals(0, new BigDecimal("108.7500").compareTo(result));
    }

    @Test
    void convertToUsd_gbp_convertsCorrectly() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("100.00"), "GBP");
        assertNotNull(result);
        assertEquals(0, new BigDecimal("126.5000").compareTo(result));
    }

    @Test
    void convertToUsd_jpy_convertsCorrectly() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("10000"), "JPY");
        assertNotNull(result);
        assertEquals(0, new BigDecimal("71.0000").compareTo(result));
    }

    @Test
    void convertToUsd_unsupportedCurrency_returnsNull() {
        assertNull(converter.convertToUsd(new BigDecimal("100.00"), "XYZ"));
    }

    @Test
    void convertToUsd_nullAmount_returnsNull() {
        assertNull(converter.convertToUsd(null, "USD"));
    }

    @Test
    void convertToUsd_nullCurrency_returnsNull() {
        assertNull(converter.convertToUsd(new BigDecimal("100.00"), null));
    }

    @Test
    void convertToUsd_lowercaseCurrency_convertsCorrectly() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("100.00"), "eur");
        assertNotNull(result);
        assertTrue(result.compareTo(BigDecimal.ZERO) > 0);
    }

    // --- convertFromUsd ---

    @Test
    void convertFromUsd_usd_returnsSameAmount() {
        BigDecimal result = converter.convertFromUsd(new BigDecimal("100.00"), "USD");
        assertEquals(new BigDecimal("100.00"), result);
    }

    @Test
    void convertFromUsd_eur_convertsCorrectly() {
        BigDecimal result = converter.convertFromUsd(new BigDecimal("108.75"), "EUR");
        assertNotNull(result);
        assertEquals(0, new BigDecimal("100.0000").compareTo(result));
    }

    @Test
    void convertFromUsd_unsupportedCurrency_returnsNull() {
        assertNull(converter.convertFromUsd(new BigDecimal("100.00"), "XYZ"));
    }

    @Test
    void convertFromUsd_nullAmount_returnsNull() {
        assertNull(converter.convertFromUsd(null, "EUR"));
    }

    @Test
    void convertFromUsd_nullCurrency_returnsNull() {
        assertNull(converter.convertFromUsd(new BigDecimal("100.00"), null));
    }

    @Test
    void convertFromUsd_lowercaseCurrency_convertsCorrectly() {
        BigDecimal result = converter.convertFromUsd(new BigDecimal("100.00"), "gbp");
        assertNotNull(result);
        assertTrue(result.compareTo(BigDecimal.ZERO) > 0);
    }

    // --- isSupported ---

    @ParameterizedTest
    @ValueSource(strings = {"USD", "EUR", "GBP", "CAD", "AUD", "JPY", "CHF", "CNY", "INR", "BRL"})
    void isSupported_supportedCurrencies_returnsTrue(String currency) {
        assertTrue(converter.isSupported(currency));
    }

    @ParameterizedTest
    @ValueSource(strings = {"usd", "eur", "gbp"})
    void isSupported_lowercaseSupportedCurrencies_returnsTrue(String currency) {
        assertTrue(converter.isSupported(currency));
    }

    @Test
    void isSupported_unsupportedCurrency_returnsFalse() {
        assertFalse(converter.isSupported("XYZ"));
    }

    @Test
    void isSupported_nullCurrency_returnsFalse() {
        assertFalse(converter.isSupported(null));
    }
}
