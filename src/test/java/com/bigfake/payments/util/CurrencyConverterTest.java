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
    void convertToUsd_usd_returnsOriginalAmount() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("100.00"), "USD");
        assertEquals(new BigDecimal("100.00"), result);
    }

    @Test
    void convertToUsd_eur_convertsCorrectly() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("100.00"), "EUR");
        // 100 * 1.0875 = 108.75
        assertEquals(new BigDecimal("108.7500"), result);
    }

    @Test
    void convertToUsd_gbp_convertsCorrectly() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("50.00"), "GBP");
        // 50 * 1.2650 = 63.25
        assertEquals(new BigDecimal("63.2500"), result);
    }

    @Test
    void convertToUsd_jpy_convertsCorrectly() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("10000"), "JPY");
        // 10000 * 0.0071 = 71.00
        assertEquals(new BigDecimal("71.0000"), result);
    }

    @Test
    void convertToUsd_caseInsensitive() {
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
    void convertToUsd_allSupportedCurrencies() {
        String[] currencies = {"USD", "EUR", "GBP", "CAD", "AUD", "JPY", "CHF", "CNY", "INR", "BRL"};
        for (String currency : currencies) {
            BigDecimal result = converter.convertToUsd(new BigDecimal("100.00"), currency);
            assertNotNull(result, "Expected non-null result for currency: " + currency);
            assertTrue(result.compareTo(BigDecimal.ZERO) > 0, "Expected positive result for currency: " + currency);
        }
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
        // 108.75 / 1.0875 = 100.0
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
    void convertFromUsd_caseInsensitive() {
        BigDecimal result = converter.convertFromUsd(new BigDecimal("100.00"), "gbp");
        assertNotNull(result);
        assertTrue(result.compareTo(BigDecimal.ZERO) > 0);
    }

    // --- isSupported ---

    @Test
    void isSupported_supportedCurrency_returnsTrue() {
        assertTrue(converter.isSupported("USD"));
        assertTrue(converter.isSupported("EUR"));
        assertTrue(converter.isSupported("GBP"));
    }

    @Test
    void isSupported_unsupportedCurrency_returnsFalse() {
        assertFalse(converter.isSupported("XYZ"));
        assertFalse(converter.isSupported("ABC"));
    }

    @Test
    void isSupported_null_returnsFalse() {
        assertFalse(converter.isSupported(null));
    }

    @Test
    void isSupported_caseInsensitive() {
        assertTrue(converter.isSupported("usd"));
        assertTrue(converter.isSupported("Eur"));
    }

    // --- Roundtrip test ---

    @Test
    void convertToUsd_andBack_approximateRoundtrip() {
        BigDecimal original = new BigDecimal("100.00");
        BigDecimal inUsd = converter.convertToUsd(original, "EUR");
        BigDecimal backToEur = converter.convertFromUsd(inUsd, "EUR");
        // Should be approximately equal (within rounding)
        assertTrue(original.subtract(backToEur).abs().compareTo(new BigDecimal("0.01")) < 0);
    }
}
