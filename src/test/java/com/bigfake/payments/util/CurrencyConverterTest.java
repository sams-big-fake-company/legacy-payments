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
            "10000,  JPY, 71.0000",
            "100.00, CHF, 111.5000",
            "100.00, CNY, 13.8000",
            "10000,  INR, 121.0000",
            "100.00, BRL, 20.5000"
    })
    void convertToUsd_appliesRateAndScalesToFourDecimals(String amount, String currency, String expected) {
        assertEquals(new BigDecimal(expected), converter.convertToUsd(new BigDecimal(amount), currency));
    }

    @Test
    void convertToUsd_usdReturnsAmountUnchanged() {
        BigDecimal amount = new BigDecimal("12.345");

        assertEquals(amount, converter.convertToUsd(amount, "USD"));
    }

    @Test
    void convertToUsd_isCaseInsensitive() {
        assertEquals(converter.convertToUsd(new BigDecimal("50.00"), "EUR"),
                converter.convertToUsd(new BigDecimal("50.00"), "eur"));
    }

    @Test
    void convertToUsd_roundsHalfUp() {
        // 0.00005 EUR * 1.0875 = 0.000054375 -> rounds to 0.0001 at scale 4
        assertEquals(new BigDecimal("0.0001"), converter.convertToUsd(new BigDecimal("0.00005"), "EUR"));
    }

    @Test
    void convertToUsd_zeroAmountConvertsToZero() {
        assertEquals(new BigDecimal("0.0000"), converter.convertToUsd(BigDecimal.ZERO, "EUR"));
    }

    @Test
    void convertToUsd_nullAmountReturnsNull() {
        assertNull(converter.convertToUsd(null, "EUR"));
    }

    @Test
    void convertToUsd_nullCurrencyReturnsNull() {
        assertNull(converter.convertToUsd(BigDecimal.TEN, null));
    }

    @Test
    void convertToUsd_unsupportedCurrencyReturnsNull() {
        assertNull(converter.convertToUsd(BigDecimal.TEN, "ZZZ"));
    }

    @ParameterizedTest
    @CsvSource({
            "108.75, EUR, 100.0000",
            "126.50, GBP, 100.0000",
            "74.50,  CAD, 100.0000",
            "71.00,  JPY, 10000.0000"
    })
    void convertFromUsd_dividesByRateAndScalesToFourDecimals(String amountUsd, String currency, String expected) {
        assertEquals(new BigDecimal(expected), converter.convertFromUsd(new BigDecimal(amountUsd), currency));
    }

    @Test
    void convertFromUsd_usdReturnsAmountUnchanged() {
        BigDecimal amount = new BigDecimal("99.999");

        assertEquals(amount, converter.convertFromUsd(amount, "usd"));
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

    @Test
    void roundTripConversion_returnsApproximatelyOriginalAmount() {
        BigDecimal original = new BigDecimal("250.00");

        BigDecimal usd = converter.convertToUsd(original, "GBP");
        BigDecimal back = converter.convertFromUsd(usd, "GBP");

        assertEquals(0, back.compareTo(original));
    }

    @ParameterizedTest
    @ValueSource(strings = {"USD", "eur", "GbP", "CAD", "AUD", "JPY", "CHF", "CNY", "INR", "BRL"})
    void isSupported_returnsTrueForKnownCurrencies(String currency) {
        assertTrue(converter.isSupported(currency));
    }

    @Test
    void isSupported_returnsFalseForNullOrUnknown() {
        assertFalse(converter.isSupported(null));
        assertFalse(converter.isSupported("ZZZ"));
        assertFalse(converter.isSupported(""));
    }
}
