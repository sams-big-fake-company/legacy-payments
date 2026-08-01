package com.bigfake.payments.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

class CurrencyConverterTest {

    private CurrencyConverter converter;

    @BeforeEach
    void setUp() {
        converter = new CurrencyConverter();
    }

    // --- convertToUsd ---

    @Test
    void convertToUsd_usd_returnsSameAmount() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("100.00"), "USD");
        assertEquals(new BigDecimal("100.00"), result);
    }

    @Test
    void convertToUsd_eur_convertsCorrectly() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("100.00"), "EUR");
        // 100 * 1.0875 = 108.7500
        assertEquals(new BigDecimal("108.7500"), result);
    }

    @Test
    void convertToUsd_gbp_convertsCorrectly() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("100.00"), "GBP");
        // 100 * 1.2650 = 126.5000
        assertEquals(new BigDecimal("126.5000"), result);
    }

    @Test
    void convertToUsd_jpy_convertsCorrectly() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("10000"), "JPY");
        // 10000 * 0.0071 = 71.0000
        assertEquals(new BigDecimal("71.0000"), result);
    }

    @Test
    void convertToUsd_lowercaseCurrency_convertsCorrectly() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("100.00"), "eur");
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

    @Test
    void convertToUsd_cad_convertsCorrectly() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("200.00"), "CAD");
        // 200 * 0.7450 = 149.0000
        assertEquals(new BigDecimal("149.0000"), result);
    }

    @Test
    void convertToUsd_aud_convertsCorrectly() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("50.00"), "AUD");
        // 50 * 0.6520 = 32.6000
        assertEquals(new BigDecimal("32.6000"), result);
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
        // 108.75 / 1.0875 = 100.0000
        assertEquals(new BigDecimal("100.0000"), result);
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
        // 100 / 1.2650
        assertEquals(new BigDecimal("100.00").divide(new BigDecimal("1.2650"), 4, RoundingMode.HALF_UP), result);
    }

    // --- isSupported ---

    @Test
    void isSupported_usd_true() {
        assertTrue(converter.isSupported("USD"));
    }

    @Test
    void isSupported_eur_true() {
        assertTrue(converter.isSupported("EUR"));
    }

    @Test
    void isSupported_lowercaseGbp_true() {
        assertTrue(converter.isSupported("gbp"));
    }

    @Test
    void isSupported_unknownCurrency_false() {
        assertFalse(converter.isSupported("XYZ"));
    }

    @Test
    void isSupported_null_false() {
        assertFalse(converter.isSupported(null));
    }

    @Test
    void isSupported_allConfiguredCurrencies() {
        String[] currencies = {"USD", "EUR", "GBP", "CAD", "AUD", "JPY", "CHF", "CNY", "INR", "BRL"};
        for (String currency : currencies) {
            assertTrue(converter.isSupported(currency), "Expected " + currency + " to be supported");
        }
    }
}
