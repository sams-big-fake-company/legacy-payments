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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for CurrencyConverter and its hardcoded rate table.
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
            "10000.00, JPY, 71.0000",
            "100.00, CHF, 111.5000",
            "100.00, CNY, 13.8000",
            "1000.00, INR, 12.1000",
            "100.00, BRL, 20.5000"
    })
    void convertToUsd_appliesRateAndScalesToFourDecimals(String amount, String currency, String expected) {
        BigDecimal result = converter.convertToUsd(new BigDecimal(amount), currency);

        assertEquals(new BigDecimal(expected), result);
        assertEquals(4, result.scale());
    }

    @Test
    void convertToUsd_returnsAmountUnchangedForUsd() {
        BigDecimal amount = new BigDecimal("42.42");

        assertEquals(amount, converter.convertToUsd(amount, "USD"));
    }

    @Test
    void convertToUsd_isCaseInsensitive() {
        assertEquals(converter.convertToUsd(new BigDecimal("50.00"), "EUR"),
                converter.convertToUsd(new BigDecimal("50.00"), "eur"));
    }

    @Test
    void convertToUsd_returnsNullForUnsupportedCurrency() {
        assertNull(converter.convertToUsd(new BigDecimal("10.00"), "XYZ"));
    }

    @Test
    void convertToUsd_returnsNullForNullArguments() {
        assertNull(converter.convertToUsd(null, "EUR"));
        assertNull(converter.convertToUsd(new BigDecimal("10.00"), null));
    }

    @Test
    void convertToUsd_handlesZero() {
        assertEquals(new BigDecimal("0.0000"), converter.convertToUsd(BigDecimal.ZERO, "EUR"));
    }

    @Test
    void convertFromUsd_dividesByRate() {
        // 108.75 USD / 1.0875 = 100 EUR
        assertEquals(new BigDecimal("100.0000"), converter.convertFromUsd(new BigDecimal("108.75"), "EUR"));
    }

    @Test
    void convertFromUsd_roundsHalfUpToFourDecimals() {
        // 1 USD / 0.0071 = 140.845070... -> 140.8451
        assertEquals(new BigDecimal("140.8451"), converter.convertFromUsd(BigDecimal.ONE, "JPY"));
    }

    @Test
    void convertFromUsd_returnsAmountUnchangedForUsd() {
        BigDecimal amount = new BigDecimal("13.37");

        assertEquals(amount, converter.convertFromUsd(amount, "usd"));
    }

    @Test
    void convertFromUsd_returnsNullForUnsupportedCurrency() {
        assertNull(converter.convertFromUsd(new BigDecimal("10.00"), "ZZZ"));
    }

    @Test
    void convertFromUsd_returnsNullForNullArguments() {
        assertNull(converter.convertFromUsd(null, "EUR"));
        assertNull(converter.convertFromUsd(new BigDecimal("10.00"), null));
    }

    @Test
    void convertRoundTrip_returnsOriginalAmount() {
        BigDecimal usd = converter.convertToUsd(new BigDecimal("200.00"), "GBP");

        assertEquals(0, new BigDecimal("200.00").compareTo(converter.convertFromUsd(usd, "GBP")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"USD", "EUR", "GBP", "CAD", "AUD", "JPY", "CHF", "CNY", "INR", "BRL", "eur"})
    void isSupported_returnsTrueForKnownCurrencies(String currency) {
        assertTrue(converter.isSupported(currency));
    }

    @Test
    void isSupported_returnsFalseForUnknownOrNullCurrency() {
        assertFalse(converter.isSupported("XYZ"));
        assertFalse(converter.isSupported(null));
        assertFalse(converter.isSupported(""));
    }
}
