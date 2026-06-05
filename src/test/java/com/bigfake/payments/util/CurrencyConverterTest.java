package com.bigfake.payments.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CurrencyConverterTest {

    private CurrencyConverter converter;

    @BeforeEach
    void setUp() {
        converter = new CurrencyConverter();
    }

    @Test
    void convertToUsd_usdReturnsOriginalAmount() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("100.00"), "USD");
        assertEquals(new BigDecimal("100.00"), result);
    }

    @Test
    void convertToUsd_eurConvertsCorrectly() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("100.00"), "EUR");
        // 100 * 1.0875 = 108.7500
        assertEquals(new BigDecimal("108.7500"), result);
    }

    @Test
    void convertToUsd_gbpConvertsCorrectly() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("50.00"), "GBP");
        // 50 * 1.2650 = 63.2500
        assertEquals(new BigDecimal("63.2500"), result);
    }

    @Test
    void convertToUsd_jpyConvertsCorrectly() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("10000"), "JPY");
        // 10000 * 0.0071 = 71.0000
        assertEquals(new BigDecimal("71.0000"), result);
    }

    @Test
    void convertToUsd_caseInsensitiveCurrency() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("100.00"), "eur");
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
    void convertFromUsd_usdReturnsOriginalAmount() {
        BigDecimal result = converter.convertFromUsd(new BigDecimal("100.00"), "USD");
        assertEquals(new BigDecimal("100.00"), result);
    }

    @Test
    void convertFromUsd_eurConvertsCorrectly() {
        BigDecimal result = converter.convertFromUsd(new BigDecimal("108.75"), "EUR");
        // 108.75 / 1.0875 = 100.0000
        assertEquals(new BigDecimal("100.0000"), result);
    }

    @Test
    void convertFromUsd_gbpConvertsCorrectly() {
        BigDecimal result = converter.convertFromUsd(new BigDecimal("126.50"), "GBP");
        // 126.50 / 1.2650 = 100.0000
        assertEquals(new BigDecimal("100.0000"), result);
    }

    @Test
    void convertFromUsd_caseInsensitiveCurrency() {
        BigDecimal result = converter.convertFromUsd(new BigDecimal("100.00"), "gbp");
        assertNotNull(result);
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
    void isSupported_supportedCurrenciesReturnTrue() {
        assertTrue(converter.isSupported("USD"));
        assertTrue(converter.isSupported("EUR"));
        assertTrue(converter.isSupported("GBP"));
        assertTrue(converter.isSupported("CAD"));
        assertTrue(converter.isSupported("AUD"));
        assertTrue(converter.isSupported("JPY"));
        assertTrue(converter.isSupported("CHF"));
        assertTrue(converter.isSupported("CNY"));
        assertTrue(converter.isSupported("INR"));
        assertTrue(converter.isSupported("BRL"));
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

    @Test
    void isSupported_caseInsensitive() {
        assertTrue(converter.isSupported("usd"));
        assertTrue(converter.isSupported("Eur"));
    }
}
