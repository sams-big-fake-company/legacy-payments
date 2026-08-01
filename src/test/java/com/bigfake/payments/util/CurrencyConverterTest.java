package com.bigfake.payments.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

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
    void convertToUsd_usd_returnsSameAmount() {
        BigDecimal amount = new BigDecimal("100.00");
        assertEquals(amount, converter.convertToUsd(amount, "USD"));
    }

    @ParameterizedTest
    @CsvSource({
            "EUR, 100.00, 108.7500",
            "GBP, 100.00, 126.5000",
            "CAD, 100.00, 74.5000",
            "JPY, 1000.00, 7.1000",
            "BRL, 100.00, 20.5000"
    })
    void convertToUsd_supportedCurrencies_appliesRate(String currency, String amount, String expected) {
        assertEquals(new BigDecimal(expected), converter.convertToUsd(new BigDecimal(amount), currency));
    }

    @Test
    void convertToUsd_lowercaseCurrency_isAccepted() {
        assertEquals(new BigDecimal("108.7500"),
                converter.convertToUsd(new BigDecimal("100.00"), "eur"));
    }

    @Test
    void convertToUsd_unsupportedCurrency_returnsNull() {
        assertNull(converter.convertToUsd(new BigDecimal("100.00"), "ZWL"));
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
    void convertFromUsd_usd_returnsSameAmount() {
        BigDecimal amount = new BigDecimal("50.00");
        assertEquals(amount, converter.convertFromUsd(amount, "USD"));
    }

    @ParameterizedTest
    @CsvSource({
            "EUR, 108.75, 100.0000",
            "GBP, 126.50, 100.0000",
            "CAD, 74.50, 100.0000"
    })
    void convertFromUsd_supportedCurrencies_dividesByRate(String currency, String amountUsd, String expected) {
        assertEquals(new BigDecimal(expected), converter.convertFromUsd(new BigDecimal(amountUsd), currency));
    }

    @Test
    void convertFromUsd_roundsToFourDecimals() {
        assertEquals(new BigDecimal("91.9540"),
                converter.convertFromUsd(new BigDecimal("100.00"), "EUR"));
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

    @ParameterizedTest
    @ValueSource(strings = {"USD", "EUR", "GBP", "CAD", "AUD", "JPY", "CHF", "CNY", "INR", "BRL"})
    void isSupported_supportedCurrencies_returnsTrue(String currency) {
        assertTrue(converter.isSupported(currency));
    }

    @Test
    void isSupported_lowercase_returnsTrue() {
        assertTrue(converter.isSupported("jpy"));
    }

    @Test
    void isSupported_unsupported_returnsFalse() {
        assertFalse(converter.isSupported("ZWL"));
    }

    @Test
    void isSupported_null_returnsFalse() {
        assertFalse(converter.isSupported(null));
    }
}
