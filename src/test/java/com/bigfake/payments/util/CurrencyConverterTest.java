package com.bigfake.payments.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CurrencyConverter.
 */
class CurrencyConverterTest {

    private CurrencyConverter converter;

    @BeforeEach
    void setUp() {
        converter = new CurrencyConverter();
    }

    @Test
    void convertToUsd_usdAmount_returnsSameAmount() {
        BigDecimal amount = new BigDecimal("123.45");
        assertEquals(amount, converter.convertToUsd(amount, "USD"));
    }

    @Test
    void convertToUsd_eur_appliesRate() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("100.00"), "EUR");
        assertEquals(new BigDecimal("108.7500"), result);
    }

    @Test
    void convertToUsd_jpy_appliesRate() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("1000"), "JPY");
        assertEquals(new BigDecimal("7.1000"), result);
    }

    @Test
    void convertToUsd_lowercaseCurrency_isNormalized() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("100.00"), "gbp");
        assertEquals(new BigDecimal("126.5000"), result);
    }

    @Test
    void convertToUsd_nullAmount_returnsNull() {
        assertNull(converter.convertToUsd(null, "USD"));
    }

    @Test
    void convertToUsd_nullCurrency_returnsNull() {
        assertNull(converter.convertToUsd(BigDecimal.TEN, null));
    }

    @Test
    void convertToUsd_unsupportedCurrency_returnsNull() {
        assertNull(converter.convertToUsd(BigDecimal.TEN, "ZWL"));
    }

    @Test
    void convertToUsd_zeroAmount_returnsZeroScaled() {
        BigDecimal result = converter.convertToUsd(BigDecimal.ZERO, "EUR");
        assertEquals(0, result.compareTo(BigDecimal.ZERO));
    }

    @Test
    void convertFromUsd_usdTarget_returnsSameAmount() {
        BigDecimal amount = new BigDecimal("50.00");
        assertEquals(amount, converter.convertFromUsd(amount, "USD"));
    }

    @Test
    void convertFromUsd_eur_appliesInverseRate() {
        BigDecimal result = converter.convertFromUsd(new BigDecimal("108.75"), "EUR");
        assertEquals(new BigDecimal("100.0000"), result);
    }

    @Test
    void convertFromUsd_lowercaseCurrency_isNormalized() {
        BigDecimal result = converter.convertFromUsd(new BigDecimal("126.50"), "gbp");
        assertEquals(new BigDecimal("100.0000"), result);
    }

    @Test
    void convertFromUsd_nullAmount_returnsNull() {
        assertNull(converter.convertFromUsd(null, "EUR"));
    }

    @Test
    void convertFromUsd_nullCurrency_returnsNull() {
        assertNull(converter.convertFromUsd(BigDecimal.TEN, null));
    }

    @Test
    void convertFromUsd_unsupportedCurrency_returnsNull() {
        assertNull(converter.convertFromUsd(BigDecimal.TEN, "ZWL"));
    }

    @Test
    void roundTrip_convertToAndFromUsd_preservesValue() {
        BigDecimal original = new BigDecimal("250.00");
        BigDecimal inUsd = converter.convertToUsd(original, "CAD");
        BigDecimal back = converter.convertFromUsd(inUsd, "CAD");
        assertEquals(0, back.compareTo(new BigDecimal("250.0000")));
    }

    @Test
    void isSupported_knownCurrencies_returnsTrue() {
        assertTrue(converter.isSupported("USD"));
        assertTrue(converter.isSupported("EUR"));
        assertTrue(converter.isSupported("brl"));
    }

    @Test
    void isSupported_unknownOrNull_returnsFalse() {
        assertFalse(converter.isSupported("ZWL"));
        assertFalse(converter.isSupported(null));
        assertFalse(converter.isSupported(""));
    }
}
