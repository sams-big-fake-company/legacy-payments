package com.bigfake.payments.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

class CurrencyConverterTest {

    private CurrencyConverter converter;

    @BeforeEach
    void setUp() {
        converter = new CurrencyConverter();
    }

    @Test
    void convertToUsd_sameUsd_returnsOriginalAmount() {
        BigDecimal amount = new BigDecimal("100.00");
        BigDecimal result = converter.convertToUsd(amount, "USD");
        assertEquals(amount, result);
    }

    @Test
    void convertToUsd_fromEur() {
        BigDecimal amount = new BigDecimal("100.00");
        BigDecimal result = converter.convertToUsd(amount, "EUR");
        assertNotNull(result);
        BigDecimal expected = new BigDecimal("100.00").multiply(new BigDecimal("1.0875"))
                .setScale(4, RoundingMode.HALF_UP);
        assertEquals(expected, result);
    }

    @Test
    void convertToUsd_fromGbp() {
        BigDecimal amount = new BigDecimal("50.00");
        BigDecimal result = converter.convertToUsd(amount, "GBP");
        assertNotNull(result);
        BigDecimal expected = new BigDecimal("50.00").multiply(new BigDecimal("1.2650"))
                .setScale(4, RoundingMode.HALF_UP);
        assertEquals(expected, result);
    }

    @Test
    void convertToUsd_fromJpy() {
        BigDecimal amount = new BigDecimal("10000");
        BigDecimal result = converter.convertToUsd(amount, "JPY");
        assertNotNull(result);
        BigDecimal expected = new BigDecimal("10000").multiply(new BigDecimal("0.0071"))
                .setScale(4, RoundingMode.HALF_UP);
        assertEquals(expected, result);
    }

    @Test
    void convertToUsd_unsupportedCurrency_returnsNull() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("100.00"), "XYZ");
        assertNull(result);
    }

    @Test
    void convertToUsd_nullAmount_returnsNull() {
        BigDecimal result = converter.convertToUsd(null, "USD");
        assertNull(result);
    }

    @Test
    void convertToUsd_nullCurrency_returnsNull() {
        BigDecimal result = converter.convertToUsd(new BigDecimal("100.00"), null);
        assertNull(result);
    }

    @Test
    void convertToUsd_caseInsensitive() {
        BigDecimal amount = new BigDecimal("100.00");
        BigDecimal resultLower = converter.convertToUsd(amount, "eur");
        BigDecimal resultUpper = converter.convertToUsd(amount, "EUR");
        assertEquals(resultUpper, resultLower);
    }

    @Test
    void convertFromUsd_toEur() {
        BigDecimal amountUsd = new BigDecimal("108.75");
        BigDecimal result = converter.convertFromUsd(amountUsd, "EUR");
        assertNotNull(result);
        BigDecimal expected = new BigDecimal("108.75").divide(new BigDecimal("1.0875"), 4, RoundingMode.HALF_UP);
        assertEquals(expected, result);
    }

    @Test
    void convertFromUsd_sameUsd_returnsOriginal() {
        BigDecimal amount = new BigDecimal("200.00");
        BigDecimal result = converter.convertFromUsd(amount, "USD");
        assertEquals(amount, result);
    }

    @Test
    void convertFromUsd_unsupportedCurrency_returnsNull() {
        BigDecimal result = converter.convertFromUsd(new BigDecimal("100.00"), "XYZ");
        assertNull(result);
    }

    @Test
    void convertFromUsd_nullAmount_returnsNull() {
        BigDecimal result = converter.convertFromUsd(null, "EUR");
        assertNull(result);
    }

    @Test
    void convertFromUsd_nullCurrency_returnsNull() {
        BigDecimal result = converter.convertFromUsd(new BigDecimal("100.00"), null);
        assertNull(result);
    }

    @Test
    void convertFromUsd_caseInsensitive() {
        BigDecimal amount = new BigDecimal("100.00");
        BigDecimal resultLower = converter.convertFromUsd(amount, "gbp");
        BigDecimal resultUpper = converter.convertFromUsd(amount, "GBP");
        assertEquals(resultUpper, resultLower);
    }

    @ParameterizedTest
    @ValueSource(strings = {"USD", "EUR", "GBP", "CAD", "AUD", "JPY", "CHF", "CNY", "INR", "BRL"})
    void isSupported_supportedCurrencies(String currency) {
        assertTrue(converter.isSupported(currency));
    }

    @ParameterizedTest
    @ValueSource(strings = {"XYZ", "ABC", "MXN", "KRW"})
    void isSupported_unsupportedCurrencies(String currency) {
        assertFalse(converter.isSupported(currency));
    }

    @Test
    void isSupported_null_returnsFalse() {
        assertFalse(converter.isSupported(null));
    }

    @Test
    void isSupported_caseInsensitive() {
        assertTrue(converter.isSupported("eur"));
        assertTrue(converter.isSupported("Gbp"));
    }
}
