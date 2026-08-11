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
            "100.00, AUD, 65.2000",
            "1000.00, JPY, 7.1000",
            "100.00, CHF, 111.5000",
            "100.00, CNY, 13.8000",
            "100.00, INR, 1.2100",
            "100.00, BRL, 20.5000"
    })
    void convertToUsd_supportedCurrencies_appliesRate(String amount, String currency, String expected) {
        assertEquals(new BigDecimal(expected), converter.convertToUsd(new BigDecimal(amount), currency));
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
    void convertToUsd_roundsToFourDecimalPlacesHalfUp() {
        assertEquals(new BigDecimal("0.0122"), converter.convertToUsd(new BigDecimal("1.005"), "INR"));
    }

    @Test
    void convertToUsd_unsupportedCurrency_returnsNull() {
        assertNull(converter.convertToUsd(new BigDecimal("10.00"), "ZZZ"));
    }

    @Test
    void convertToUsd_nullInputs_returnNull() {
        assertNull(converter.convertToUsd(null, "EUR"));
        assertNull(converter.convertToUsd(new BigDecimal("10.00"), null));
    }

    @Test
    void convertToUsd_zeroAmount_returnsZero() {
        assertEquals(new BigDecimal("0.0000"), converter.convertToUsd(BigDecimal.ZERO, "EUR"));
    }

    @ParameterizedTest
    @CsvSource({
            "108.75, EUR, 100.0000",
            "126.50, GBP, 100.0000",
            "74.50, CAD, 100.0000",
            "7.10, JPY, 1000.0000"
    })
    void convertFromUsd_supportedCurrencies_dividesByRate(String amountUsd, String currency, String expected) {
        assertEquals(new BigDecimal(expected), converter.convertFromUsd(new BigDecimal(amountUsd), currency));
    }

    @Test
    void convertFromUsd_usd_returnsSameInstance() {
        BigDecimal amount = new BigDecimal("13.37");

        assertSame(amount, converter.convertFromUsd(amount, "usd"));
    }

    @Test
    void convertFromUsd_unsupportedCurrency_returnsNull() {
        assertNull(converter.convertFromUsd(new BigDecimal("10.00"), "XYZ"));
    }

    @Test
    void convertFromUsd_nullInputs_returnNull() {
        assertNull(converter.convertFromUsd(null, "EUR"));
        assertNull(converter.convertFromUsd(new BigDecimal("10.00"), null));
    }

    @Test
    void roundTrip_conversionIsStableWithinRounding() {
        BigDecimal usd = converter.convertToUsd(new BigDecimal("250.00"), "EUR");
        BigDecimal backToEur = converter.convertFromUsd(usd, "EUR");

        assertEquals(new BigDecimal("250.0000"), backToEur);
    }

    @ParameterizedTest
    @ValueSource(strings = {"USD", "eur", "GbP", "JPY", "BRL"})
    void isSupported_knownCurrencies_returnTrue(String currency) {
        assertTrue(converter.isSupported(currency));
    }

    @Test
    void isSupported_unknownOrNullCurrency_returnsFalse() {
        assertFalse(converter.isSupported("ZZZ"));
        assertFalse(converter.isSupported(null));
    }
}
