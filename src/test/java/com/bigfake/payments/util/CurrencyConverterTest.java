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
    void convertToUsd_usdReturnsSameAmount() {
        BigDecimal amount = new BigDecimal("123.45");
        assertEquals(amount, converter.convertToUsd(amount, "USD"));
    }

    @Test
    void convertToUsd_eurAppliesRate() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("100.00"), "EUR");
        assertEquals(new BigDecimal("108.7500"), result);
    }

    @Test
    void convertToUsd_lowercaseCurrencyIsNormalized() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("100.00"), "gbp");
        assertEquals(new BigDecimal("126.5000"), result);
    }

    @Test
    void convertToUsd_nullAmountReturnsNull() {
        assertNull(converter.convertToUsd(null, "USD"));
    }

    @Test
    void convertToUsd_nullCurrencyReturnsNull() {
        assertNull(converter.convertToUsd(BigDecimal.TEN, null));
    }

    @Test
    void convertToUsd_unsupportedCurrencyReturnsNull() {
        assertNull(converter.convertToUsd(BigDecimal.TEN, "XYZ"));
    }

    @Test
    void convertFromUsd_usdReturnsSameAmount() {
        BigDecimal amount = new BigDecimal("50.00");
        assertEquals(amount, converter.convertFromUsd(amount, "USD"));
    }

    @Test
    void convertFromUsd_eurDividesByRate() {
        BigDecimal result = converter.convertFromUsd(new BigDecimal("108.75"), "EUR");
        assertEquals(new BigDecimal("100.0000"), result);
    }

    @Test
    void convertFromUsd_nullAmountReturnsNull() {
        assertNull(converter.convertFromUsd(null, "EUR"));
    }

    @Test
    void convertFromUsd_nullCurrencyReturnsNull() {
        assertNull(converter.convertFromUsd(BigDecimal.TEN, null));
    }

    @Test
    void convertFromUsd_unsupportedCurrencyReturnsNull() {
        assertNull(converter.convertFromUsd(BigDecimal.TEN, "ZZZ"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"USD", "EUR", "GBP", "CAD", "AUD", "JPY", "CHF", "CNY", "INR", "BRL", "usd", "jpy"})
    void isSupported_returnsTrueForSupportedCurrencies(String currency) {
        assertTrue(converter.isSupported(currency));
    }

    @Test
    void isSupported_returnsFalseForUnsupportedCurrency() {
        assertFalse(converter.isSupported("XYZ"));
    }

    @Test
    void isSupported_returnsFalseForNull() {
        assertFalse(converter.isSupported(null));
    }
}
