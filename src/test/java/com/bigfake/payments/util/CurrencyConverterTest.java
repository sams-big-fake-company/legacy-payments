package com.bigfake.payments.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for CurrencyConverter.
 */
class CurrencyConverterTest {

    private CurrencyConverter converter;

    @BeforeEach
    void setUp() {
        converter = new CurrencyConverter();
    }

    @ParameterizedTest
    @CsvSource({
            "100.00, EUR, 108.7500",
            "100.00, GBP, 126.5000",
            "100.00, CAD, 74.5000",
            "1000.00, JPY, 7.1000",
            "50.00, BRL, 10.2500"
    })
    void convertToUsd_supportedCurrency_appliesRateWithFourDecimals(String amount, String currency, String expected) {
        BigDecimal result = converter.convertToUsd(new BigDecimal(amount), currency);

        assertEquals(new BigDecimal(expected), result);
    }

    @Test
    void convertToUsd_usd_returnsSameInstance() {
        BigDecimal amount = new BigDecimal("42.42");

        assertSame(amount, converter.convertToUsd(amount, "USD"));
    }

    @Test
    void convertToUsd_lowercaseCurrency_isNormalized() {
        assertEquals(new BigDecimal("108.7500"), converter.convertToUsd(new BigDecimal("100.00"), "eur"));
    }

    @Test
    void convertToUsd_unsupportedCurrency_returnsNull() {
        assertNull(converter.convertToUsd(new BigDecimal("10.00"), "ZWL"));
    }

    @Test
    void convertToUsd_nullArguments_returnNull() {
        assertNull(converter.convertToUsd(null, "EUR"));
        assertNull(converter.convertToUsd(new BigDecimal("10.00"), null));
        assertNull(converter.convertToUsd(null, null));
    }

    @ParameterizedTest
    @CsvSource({
            "108.75, EUR, 100.0000",
            "126.50, GBP, 100.0000",
            "7.10, JPY, 1000.0000"
    })
    void convertFromUsd_supportedCurrency_dividesByRate(String amountUsd, String currency, String expected) {
        BigDecimal result = converter.convertFromUsd(new BigDecimal(amountUsd), currency);

        assertEquals(new BigDecimal(expected), result);
    }

    @Test
    void convertFromUsd_usd_returnsSameInstance() {
        BigDecimal amount = new BigDecimal("17.00");

        assertSame(amount, converter.convertFromUsd(amount, "usd"));
    }

    @Test
    void convertFromUsd_unsupportedCurrency_returnsNull() {
        assertNull(converter.convertFromUsd(new BigDecimal("10.00"), "ZWL"));
    }

    @Test
    void convertFromUsd_nullArguments_returnNull() {
        assertNull(converter.convertFromUsd(null, "EUR"));
        assertNull(converter.convertFromUsd(new BigDecimal("10.00"), null));
    }

    @Test
    void roundTripConversion_isStableWithinRoundingTolerance() {
        BigDecimal usd = converter.convertToUsd(new BigDecimal("250.00"), "CHF");
        BigDecimal backToChf = converter.convertFromUsd(usd, "CHF");

        assertEquals(0, backToChf.compareTo(new BigDecimal("250.0000")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"USD", "EUR", "gbp", "CAD", "AUD", "JPY", "CHF", "CNY", "INR", "BRL"})
    void isSupported_knownCurrencies_returnTrue(String currency) {
        assertTrue(converter.isSupported(currency));
    }

    @Test
    void isSupported_nullOrUnknownCurrency_returnsFalse() {
        assertFalse(converter.isSupported(null));
        assertFalse(converter.isSupported("XYZ"));
    }
}
