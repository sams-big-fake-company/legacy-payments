package com.bigfake.payments.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

class CurrencyConverterTest {

    private final CurrencyConverter converter = new CurrencyConverter();

    // ---- convertToUsd ----

    @Test
    void convertToUsd_usd_returnsSameAmount() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("100.00"), "USD");
        assertEquals(new BigDecimal("100.00"), result);
    }

    @Test
    void convertToUsd_eur_appliesRate() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("100.00"), "EUR");
        assertNotNull(result);
        assertEquals(new BigDecimal("108.7500"), result);
    }

    @Test
    void convertToUsd_gbp_appliesRate() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("100.00"), "GBP");
        assertNotNull(result);
        assertEquals(new BigDecimal("126.5000"), result);
    }

    @Test
    void convertToUsd_jpy_appliesRate() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("10000"), "JPY");
        assertNotNull(result);
        assertEquals(new BigDecimal("71.0000"), result);
    }

    @Test
    void convertToUsd_lowercaseCurrency_works() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("100.00"), "eur");
        assertNotNull(result);
        assertEquals(new BigDecimal("108.7500"), result);
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

    @ParameterizedTest
    @ValueSource(strings = {"CAD", "AUD", "CHF", "CNY", "INR", "BRL"})
    void convertToUsd_allSupportedCurrencies_returnsNonNull(String currency) {
        BigDecimal result = converter.convertToUsd(new BigDecimal("100.00"), currency);
        assertNotNull(result);
        assertTrue(result.compareTo(BigDecimal.ZERO) > 0);
    }

    // ---- convertFromUsd ----

    @Test
    void convertFromUsd_usd_returnsSameAmount() {
        BigDecimal result = converter.convertFromUsd(new BigDecimal("100.00"), "USD");
        assertEquals(new BigDecimal("100.00"), result);
    }

    @Test
    void convertFromUsd_eur_appliesInverseRate() {
        BigDecimal result = converter.convertFromUsd(new BigDecimal("108.75"), "EUR");
        assertNotNull(result);
        assertEquals(new BigDecimal("108.75").divide(new BigDecimal("1.0875"), 4, RoundingMode.HALF_UP), result);
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
    void convertFromUsd_lowercaseCurrency_works() {
        BigDecimal result = converter.convertFromUsd(new BigDecimal("100.00"), "gbp");
        assertNotNull(result);
        assertTrue(result.compareTo(BigDecimal.ZERO) > 0);
    }

    // ---- isSupported ----

    @Test
    void isSupported_supportedCurrencies_returnsTrue() {
        assertTrue(converter.isSupported("USD"));
        assertTrue(converter.isSupported("EUR"));
        assertTrue(converter.isSupported("GBP"));
        assertTrue(converter.isSupported("JPY"));
    }

    @Test
    void isSupported_unsupportedCurrency_returnsFalse() {
        assertFalse(converter.isSupported("XYZ"));
        assertFalse(converter.isSupported("ABC"));
    }

    @Test
    void isSupported_nullCurrency_returnsFalse() {
        assertFalse(converter.isSupported(null));
    }

    @Test
    void isSupported_lowercaseCurrency_returnsTrue() {
        assertTrue(converter.isSupported("usd"));
        assertTrue(converter.isSupported("eur"));
    }
}
