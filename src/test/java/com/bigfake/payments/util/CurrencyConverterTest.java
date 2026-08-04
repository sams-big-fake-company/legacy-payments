package com.bigfake.payments.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CurrencyConverter (hardcoded FX rates).
 */
class CurrencyConverterTest {

    private CurrencyConverter converter;

    @BeforeEach
    void setUp() {
        converter = new CurrencyConverter();
    }

    @ParameterizedTest
    @CsvSource({
            "EUR, 100.00, 108.7500",
            "GBP, 100.00, 126.5000",
            "CAD, 200.00, 149.0000",
            "JPY, 10000.00, 71.0000",
            "BRL, 50.00, 10.2500"
    })
    void convertToUsd_appliesRateWithFourDecimalScale(String currency, String amount, String expected) {
        BigDecimal result = converter.convertToUsd(new BigDecimal(amount), currency);

        assertEquals(new BigDecimal(expected), result);
    }

    @Test
    void convertToUsd_usdIsReturnedUnchanged() {
        BigDecimal amount = new BigDecimal("99.99");

        assertSame(amount, converter.convertToUsd(amount, "USD"));
    }

    @Test
    void convertToUsd_currencyCodeIsCaseInsensitive() {
        assertEquals(converter.convertToUsd(new BigDecimal("10.00"), "EUR"),
                converter.convertToUsd(new BigDecimal("10.00"), "eur"));
    }

    @Test
    void convertToUsd_roundsHalfUpToFourDecimals() {
        // 1.11 INR * 0.0121 = 0.013431 -> 0.0134
        assertEquals(new BigDecimal("0.0134"), converter.convertToUsd(new BigDecimal("1.11"), "INR"));
    }

    @Test
    void convertToUsd_unsupportedCurrencyReturnsNull() {
        assertNull(converter.convertToUsd(new BigDecimal("10.00"), "XYZ"));
    }

    @Test
    void convertToUsd_nullArgumentsReturnNull() {
        assertNull(converter.convertToUsd(null, "EUR"));
        assertNull(converter.convertToUsd(new BigDecimal("10.00"), null));
    }

    @Test
    void convertFromUsd_dividesByRate() {
        // 108.75 USD / 1.0875 = 100.0000 EUR
        assertEquals(new BigDecimal("100.0000"), converter.convertFromUsd(new BigDecimal("108.75"), "EUR"));
    }

    @Test
    void convertFromUsd_usdIsReturnedUnchanged() {
        BigDecimal amount = new BigDecimal("42.42");

        assertSame(amount, converter.convertFromUsd(amount, "usd"));
    }

    @Test
    void convertFromUsd_unsupportedCurrencyReturnsNull() {
        assertNull(converter.convertFromUsd(new BigDecimal("10.00"), "XYZ"));
    }

    @Test
    void convertFromUsd_nullArgumentsReturnNull() {
        assertNull(converter.convertFromUsd(null, "EUR"));
        assertNull(converter.convertFromUsd(new BigDecimal("10.00"), null));
    }

    @Test
    void convertToUsdThenBack_isRoundTripStableForEur() {
        BigDecimal usd = converter.convertToUsd(new BigDecimal("250.00"), "EUR");

        assertEquals(new BigDecimal("250.0000"), converter.convertFromUsd(usd, "EUR"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"USD", "EUR", "GBP", "CAD", "AUD", "JPY", "CHF", "CNY", "INR", "BRL", "brl"})
    void isSupported_returnsTrueForKnownCurrencies(String currency) {
        assertTrue(converter.isSupported(currency));
    }

    @Test
    void isSupported_returnsFalseForUnknownOrNullCurrency() {
        assertFalse(converter.isSupported("XYZ"));
        assertFalse(converter.isSupported(null));
    }
}
