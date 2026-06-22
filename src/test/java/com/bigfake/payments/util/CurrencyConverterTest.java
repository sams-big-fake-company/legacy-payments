package com.bigfake.payments.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

class CurrencyConverterTest {

    private CurrencyConverter currencyConverter;

    @BeforeEach
    void setUp() {
        currencyConverter = new CurrencyConverter();
    }

    @Test
    void convertToUsd_sameAsUsd() {
        BigDecimal result = currencyConverter.convertToUsd(new BigDecimal("100.00"), "USD");
        assertEquals(new BigDecimal("100.00"), result);
    }

    @Test
    void convertToUsd_fromEur() {
        BigDecimal result = currencyConverter.convertToUsd(new BigDecimal("100.00"), "EUR");
        BigDecimal expected = new BigDecimal("100.00").multiply(new BigDecimal("1.0875"))
                .setScale(4, RoundingMode.HALF_UP);
        assertEquals(expected, result);
    }

    @Test
    void convertToUsd_fromGbp() {
        BigDecimal result = currencyConverter.convertToUsd(new BigDecimal("100.00"), "GBP");
        BigDecimal expected = new BigDecimal("100.00").multiply(new BigDecimal("1.2650"))
                .setScale(4, RoundingMode.HALF_UP);
        assertEquals(expected, result);
    }

    @Test
    void convertToUsd_fromJpy() {
        BigDecimal result = currencyConverter.convertToUsd(new BigDecimal("10000"), "JPY");
        BigDecimal expected = new BigDecimal("10000").multiply(new BigDecimal("0.0071"))
                .setScale(4, RoundingMode.HALF_UP);
        assertEquals(expected, result);
    }

    @Test
    void convertToUsd_caseInsensitive() {
        BigDecimal result = currencyConverter.convertToUsd(new BigDecimal("100.00"), "eur");
        assertNotNull(result);
    }

    @Test
    void convertToUsd_nullAmount() {
        BigDecimal result = currencyConverter.convertToUsd(null, "USD");
        assertNull(result);
    }

    @Test
    void convertToUsd_nullCurrency() {
        BigDecimal result = currencyConverter.convertToUsd(new BigDecimal("100.00"), null);
        assertNull(result);
    }

    @Test
    void convertToUsd_unsupportedCurrency() {
        BigDecimal result = currencyConverter.convertToUsd(new BigDecimal("100.00"), "XYZ");
        assertNull(result);
    }

    @Test
    void convertFromUsd_sameAsUsd() {
        BigDecimal result = currencyConverter.convertFromUsd(new BigDecimal("100.00"), "USD");
        assertEquals(new BigDecimal("100.00"), result);
    }

    @Test
    void convertFromUsd_toEur() {
        BigDecimal result = currencyConverter.convertFromUsd(new BigDecimal("108.75"), "EUR");
        BigDecimal expected = new BigDecimal("108.75").divide(new BigDecimal("1.0875"), 4, RoundingMode.HALF_UP);
        assertEquals(expected, result);
    }

    @Test
    void convertFromUsd_toGbp() {
        BigDecimal result = currencyConverter.convertFromUsd(new BigDecimal("126.50"), "GBP");
        BigDecimal expected = new BigDecimal("126.50").divide(new BigDecimal("1.2650"), 4, RoundingMode.HALF_UP);
        assertEquals(expected, result);
    }

    @Test
    void convertFromUsd_nullAmount() {
        BigDecimal result = currencyConverter.convertFromUsd(null, "EUR");
        assertNull(result);
    }

    @Test
    void convertFromUsd_nullCurrency() {
        BigDecimal result = currencyConverter.convertFromUsd(new BigDecimal("100.00"), null);
        assertNull(result);
    }

    @Test
    void convertFromUsd_unsupportedCurrency() {
        BigDecimal result = currencyConverter.convertFromUsd(new BigDecimal("100.00"), "ABC");
        assertNull(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"USD", "EUR", "GBP", "CAD", "AUD", "JPY", "CHF", "CNY", "INR", "BRL"})
    void isSupported_allSupportedCurrencies(String currency) {
        assertTrue(currencyConverter.isSupported(currency));
    }

    @Test
    void isSupported_caseInsensitive() {
        assertTrue(currencyConverter.isSupported("usd"));
        assertTrue(currencyConverter.isSupported("Eur"));
    }

    @Test
    void isSupported_nullCurrency() {
        assertFalse(currencyConverter.isSupported(null));
    }

    @Test
    void isSupported_unsupportedCurrency() {
        assertFalse(currencyConverter.isSupported("XYZ"));
        assertFalse(currencyConverter.isSupported(""));
    }
}
