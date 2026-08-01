package com.bigfake.payments.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CurrencyConverterTest {

    private CurrencyConverter converter;

    @BeforeEach
    void setUp() {
        converter = new CurrencyConverter();
    }

    @Test
    void convertToUsd_usdReturnsOriginal() {
        BigDecimal amount = new BigDecimal("100.00");
        BigDecimal result = converter.convertToUsd(amount, "USD");
        assertEquals(amount, result);
    }

    @Test
    void convertToUsd_eurConverts() {
        BigDecimal amount = new BigDecimal("100.00");
        BigDecimal result = converter.convertToUsd(amount, "EUR");
        assertNotNull(result);
        assertEquals(new BigDecimal("108.7500"), result);
    }

    @Test
    void convertToUsd_gbpConverts() {
        BigDecimal amount = new BigDecimal("100.00");
        BigDecimal result = converter.convertToUsd(amount, "GBP");
        assertNotNull(result);
        assertEquals(new BigDecimal("126.5000"), result);
    }

    @Test
    void convertToUsd_jpyConverts() {
        BigDecimal amount = new BigDecimal("10000");
        BigDecimal result = converter.convertToUsd(amount, "JPY");
        assertNotNull(result);
        assertEquals(new BigDecimal("71.0000"), result);
    }

    @Test
    void convertToUsd_caseInsensitive() {
        BigDecimal amount = new BigDecimal("100.00");
        BigDecimal resultLower = converter.convertToUsd(amount, "eur");
        BigDecimal resultUpper = converter.convertToUsd(amount, "EUR");
        assertEquals(resultLower, resultUpper);
    }

    @Test
    void convertToUsd_unsupportedCurrencyReturnsNull() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("100.00"), "XYZ");
        assertNull(result);
    }

    @Test
    void convertToUsd_nullAmountReturnsNull() {
        BigDecimal result = converter.convertToUsd(null, "EUR");
        assertNull(result);
    }

    @Test
    void convertToUsd_nullCurrencyReturnsNull() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("100.00"), null);
        assertNull(result);
    }

    @Test
    void convertFromUsd_usdReturnsOriginal() {
        BigDecimal amount = new BigDecimal("100.00");
        BigDecimal result = converter.convertFromUsd(amount, "USD");
        assertEquals(amount, result);
    }

    @Test
    void convertFromUsd_eurConverts() {
        BigDecimal amountUsd = new BigDecimal("108.75");
        BigDecimal result = converter.convertFromUsd(amountUsd, "EUR");
        assertNotNull(result);
        assertEquals(new BigDecimal("100.0000"), result);
    }

    @Test
    void convertFromUsd_unsupportedCurrencyReturnsNull() {
        BigDecimal result = converter.convertFromUsd(new BigDecimal("100.00"), "XYZ");
        assertNull(result);
    }

    @Test
    void convertFromUsd_nullAmountReturnsNull() {
        BigDecimal result = converter.convertFromUsd(null, "EUR");
        assertNull(result);
    }

    @Test
    void convertFromUsd_nullCurrencyReturnsNull() {
        BigDecimal result = converter.convertFromUsd(new BigDecimal("100.00"), null);
        assertNull(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"USD", "EUR", "GBP", "CAD", "AUD", "JPY", "CHF", "CNY", "INR", "BRL"})
    void isSupported_allSupportedCurrencies(String currency) {
        assertTrue(converter.isSupported(currency));
    }

    @Test
    void isSupported_unsupportedReturnsFalse() {
        assertFalse(converter.isSupported("XYZ"));
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
