package com.bigfake.payments.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

class CurrencyConverterTest {

    private final CurrencyConverter converter = new CurrencyConverter();

    // --- convertToUsd ---

    @Test
    void convertToUsd_usd_returnsOriginalAmount() {
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
        BigDecimal result = converter.convertToUsd(new BigDecimal("50.00"), "GBP");
        // 50 * 1.2650 = 63.2500
        assertEquals(new BigDecimal("63.2500"), result);
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
    void convertToUsd_bothNull_returnsNull() {
        assertNull(converter.convertToUsd(null, null));
    }

    @Test
    void convertToUsd_cad_convertsCorrectly() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("200.00"), "CAD");
        // 200 * 0.7450 = 149.0000
        assertEquals(new BigDecimal("149.0000"), result);
    }

    @Test
    void convertToUsd_aud_convertsCorrectly() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("100.00"), "AUD");
        // 100 * 0.6520 = 65.2000
        assertEquals(new BigDecimal("65.2000"), result);
    }

    @Test
    void convertToUsd_chf_convertsCorrectly() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("100.00"), "CHF");
        // 100 * 1.1150 = 111.5000
        assertEquals(new BigDecimal("111.5000"), result);
    }

    @Test
    void convertToUsd_cny_convertsCorrectly() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("1000.00"), "CNY");
        // 1000 * 0.1380 = 138.0000
        assertEquals(new BigDecimal("138.0000"), result);
    }

    @Test
    void convertToUsd_inr_convertsCorrectly() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("10000.00"), "INR");
        // 10000 * 0.0121 = 121.0000
        assertEquals(new BigDecimal("121.0000"), result);
    }

    @Test
    void convertToUsd_brl_convertsCorrectly() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("500.00"), "BRL");
        // 500 * 0.2050 = 102.5000
        assertEquals(new BigDecimal("102.5000"), result);
    }

    // --- convertFromUsd ---

    @Test
    void convertFromUsd_usd_returnsOriginalAmount() {
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
    void convertFromUsd_gbp_convertsCorrectly() {
        BigDecimal result = converter.convertFromUsd(new BigDecimal("126.50"), "GBP");
        // 126.50 / 1.2650 = 100.0000
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
        BigDecimal result = converter.convertFromUsd(new BigDecimal("108.75"), "eur");
        assertEquals(new BigDecimal("100.0000"), result);
    }

    // --- isSupported ---

    @Test
    void isSupported_validCurrency_returnsTrue() {
        assertTrue(converter.isSupported("USD"));
        assertTrue(converter.isSupported("EUR"));
        assertTrue(converter.isSupported("GBP"));
        assertTrue(converter.isSupported("JPY"));
    }

    @Test
    void isSupported_lowercaseCurrency_returnsTrue() {
        assertTrue(converter.isSupported("usd"));
        assertTrue(converter.isSupported("eur"));
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
}
