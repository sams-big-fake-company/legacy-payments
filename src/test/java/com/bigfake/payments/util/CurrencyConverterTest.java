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
    void convertToUsd_usdReturnsOriginalAmount() {
        BigDecimal amount = new BigDecimal("100.00");
        BigDecimal result = converter.convertToUsd(amount, "USD");
        assertEquals(amount, result);
    }

    @Test
    void convertToUsd_eurConvertsCorrectly() {
        BigDecimal amount = new BigDecimal("100.00");
        BigDecimal result = converter.convertToUsd(amount, "EUR");
        // 100 * 1.0875 = 108.7500
        assertEquals(new BigDecimal("108.7500"), result);
    }

    @Test
    void convertToUsd_gbpConvertsCorrectly() {
        BigDecimal amount = new BigDecimal("50.00");
        BigDecimal result = converter.convertToUsd(amount, "GBP");
        // 50 * 1.2650 = 63.2500
        assertEquals(new BigDecimal("63.2500"), result);
    }

    @Test
    void convertToUsd_jpyConvertsCorrectly() {
        BigDecimal amount = new BigDecimal("10000");
        BigDecimal result = converter.convertToUsd(amount, "JPY");
        // 10000 * 0.0071 = 71.0000
        assertEquals(new BigDecimal("71.0000"), result);
    }

    @Test
    void convertToUsd_lowercaseCurrencyWorks() {
        BigDecimal amount = new BigDecimal("100.00");
        BigDecimal result = converter.convertToUsd(amount, "eur");
        assertNotNull(result);
        assertEquals(new BigDecimal("108.7500"), result);
    }

    @Test
    void convertToUsd_nullAmountReturnsNull() {
        assertNull(converter.convertToUsd(null, "USD"));
    }

    @Test
    void convertToUsd_nullCurrencyReturnsNull() {
        assertNull(converter.convertToUsd(new BigDecimal("100.00"), null));
    }

    @Test
    void convertToUsd_unsupportedCurrencyReturnsNull() {
        assertNull(converter.convertToUsd(new BigDecimal("100.00"), "XYZ"));
    }

    @Test
    void convertToUsd_cadConvertsCorrectly() {
        BigDecimal amount = new BigDecimal("200.00");
        BigDecimal result = converter.convertToUsd(amount, "CAD");
        // 200 * 0.7450 = 149.0000
        assertEquals(new BigDecimal("149.0000"), result);
    }

    @Test
    void convertToUsd_audConvertsCorrectly() {
        BigDecimal amount = new BigDecimal("100.00");
        BigDecimal result = converter.convertToUsd(amount, "AUD");
        // 100 * 0.6520 = 65.2000
        assertEquals(new BigDecimal("65.2000"), result);
    }

    @Test
    void convertToUsd_chfConvertsCorrectly() {
        BigDecimal amount = new BigDecimal("100.00");
        BigDecimal result = converter.convertToUsd(amount, "CHF");
        // 100 * 1.1150 = 111.5000
        assertEquals(new BigDecimal("111.5000"), result);
    }

    @Test
    void convertToUsd_cnyConvertsCorrectly() {
        BigDecimal amount = new BigDecimal("1000.00");
        BigDecimal result = converter.convertToUsd(amount, "CNY");
        // 1000 * 0.1380 = 138.0000
        assertEquals(new BigDecimal("138.0000"), result);
    }

    @Test
    void convertToUsd_inrConvertsCorrectly() {
        BigDecimal amount = new BigDecimal("10000");
        BigDecimal result = converter.convertToUsd(amount, "INR");
        // 10000 * 0.0121 = 121.0000
        assertEquals(new BigDecimal("121.0000"), result);
    }

    @Test
    void convertToUsd_brlConvertsCorrectly() {
        BigDecimal amount = new BigDecimal("500.00");
        BigDecimal result = converter.convertToUsd(amount, "BRL");
        // 500 * 0.2050 = 102.5000
        assertEquals(new BigDecimal("102.5000"), result);
    }

    @Test
    void convertToUsd_zeroAmountReturnsZero() {
        BigDecimal result = converter.convertToUsd(BigDecimal.ZERO, "EUR");
        assertEquals(new BigDecimal("0.0000"), result);
    }

    // --- convertFromUsd ---

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
        // 108.75 / 1.0875 = 100.0000
        assertEquals(new BigDecimal("100.0000"), result);
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
    void convertFromUsd_unsupportedCurrencyReturnsNull() {
        assertNull(converter.convertFromUsd(new BigDecimal("100.00"), "XYZ"));
    }

    @Test
    void convertFromUsd_lowercaseCurrencyWorks() {
        BigDecimal amount = new BigDecimal("100.00");
        BigDecimal result = converter.convertFromUsd(amount, "gbp");
        assertNotNull(result);
    }

    // --- isSupported ---

    @Test
    void isSupported_validCurrencyReturnsTrue() {
        assertTrue(converter.isSupported("USD"));
        assertTrue(converter.isSupported("EUR"));
        assertTrue(converter.isSupported("GBP"));
        assertTrue(converter.isSupported("JPY"));
    }

    @Test
    void isSupported_lowercaseReturnsTrue() {
        assertTrue(converter.isSupported("usd"));
        assertTrue(converter.isSupported("eur"));
    }

    @Test
    void isSupported_unsupportedCurrencyReturnsFalse() {
        assertFalse(converter.isSupported("XYZ"));
        assertFalse(converter.isSupported("ABC"));
    }

    @Test
    void isSupported_nullReturnsFalse() {
        assertFalse(converter.isSupported(null));
    }
}
