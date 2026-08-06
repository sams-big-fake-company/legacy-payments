package com.bigfake.payments.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
 *
 * The converter uses hardcoded rates (PAY-2876), so the expected values below
 * are derived from those constants and are deterministic.
 */
class CurrencyConverterTest {

    private CurrencyConverter converter;

    @BeforeEach
    void setUp() {
        converter = new CurrencyConverter();
    }

    @Nested
    class ConvertToUsd {

        @Test
        void returnsNullWhenAmountIsNull() {
            assertNull(converter.convertToUsd(null, "EUR"));
        }

        @Test
        void returnsNullWhenCurrencyIsNull() {
            assertNull(converter.convertToUsd(new BigDecimal("10.00"), null));
        }

        @Test
        void returnsNullForUnsupportedCurrency() {
            assertNull(converter.convertToUsd(new BigDecimal("10.00"), "XYZ"));
        }

        @Test
        void returnsAmountUnchangedForUsd() {
            BigDecimal amount = new BigDecimal("123.45");

            assertSame(amount, converter.convertToUsd(amount, "USD"));
        }

        @ParameterizedTest
        @CsvSource({
                "100.00, EUR, 108.7500",
                "100.00, GBP, 126.5000",
                "1000,   JPY, 7.1000",
                "50.00,  BRL, 10.2500"
        })
        void convertsUsingHardcodedRate(String amount, String currency, String expected) {
            assertEquals(new BigDecimal(expected), converter.convertToUsd(new BigDecimal(amount), currency));
        }

        @Test
        void currencyCodeIsCaseInsensitive() {
            assertEquals(new BigDecimal("108.7500"), converter.convertToUsd(new BigDecimal("100.00"), "eur"));
        }

        @Test
        void roundsHalfUpToFourDecimalPlaces() {
            // 0.33333 * 1.0875 = 0.3624964... -> 0.3625
            assertEquals(new BigDecimal("0.3625"), converter.convertToUsd(new BigDecimal("0.33333"), "EUR"));
        }

        @Test
        void convertsZeroAmount() {
            assertEquals(new BigDecimal("0.0000"), converter.convertToUsd(BigDecimal.ZERO, "EUR"));
        }
    }

    @Nested
    class ConvertFromUsd {

        @Test
        void returnsNullWhenAmountIsNull() {
            assertNull(converter.convertFromUsd(null, "EUR"));
        }

        @Test
        void returnsNullWhenCurrencyIsNull() {
            assertNull(converter.convertFromUsd(new BigDecimal("10.00"), null));
        }

        @Test
        void returnsNullForUnsupportedCurrency() {
            assertNull(converter.convertFromUsd(new BigDecimal("10.00"), "ZZZ"));
        }

        @Test
        void returnsAmountUnchangedForUsd() {
            BigDecimal amount = new BigDecimal("77.00");

            assertSame(amount, converter.convertFromUsd(amount, "usd"));
        }

        @ParameterizedTest
        @CsvSource({
                "108.75, EUR, 100.0000",
                "126.50, GBP, 100.0000",
                "7.10,   JPY, 1000.0000"
        })
        void invertsTheHardcodedRate(String amountUsd, String currency, String expected) {
            assertEquals(new BigDecimal(expected), converter.convertFromUsd(new BigDecimal(amountUsd), currency));
        }

        @Test
        void roundsHalfUpToFourDecimalPlaces() {
            // 100 / 1.0875 = 91.95402298... -> 91.9540
            assertEquals(new BigDecimal("91.9540"), converter.convertFromUsd(new BigDecimal("100.00"), "EUR"));
        }
    }

    @Nested
    class IsSupported {

        @ParameterizedTest
        @ValueSource(strings = {"USD", "EUR", "GBP", "CAD", "AUD", "JPY", "CHF", "CNY", "INR", "BRL", "eur"})
        void returnsTrueForKnownCurrencies(String currency) {
            assertTrue(converter.isSupported(currency));
        }

        @ParameterizedTest
        @ValueSource(strings = {"XYZ", "", "US"})
        void returnsFalseForUnknownCurrencies(String currency) {
            assertFalse(converter.isSupported(currency));
        }

        @Test
        void returnsFalseForNull() {
            assertFalse(converter.isSupported(null));
        }
    }
}
