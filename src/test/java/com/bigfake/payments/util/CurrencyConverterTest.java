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
 */
class CurrencyConverterTest {

    private CurrencyConverter converter;

    @BeforeEach
    void setUp() {
        converter = new CurrencyConverter();
    }

    @Nested
    class ConvertToUsd {

        @ParameterizedTest(name = "{1} {0} -> {2} USD")
        @CsvSource({
                "100.00, EUR, 108.7500",
                "100.00, GBP, 126.5000",
                "100.00, CAD, 74.5000",
                "100.00, AUD, 65.2000",
                "10000,  JPY, 71.0000",
                "100.00, CHF, 111.5000",
                "100.00, CNY, 13.8000",
                "100.00, INR, 1.2100",
                "100.00, BRL, 20.5000"
        })
        void appliesTheHardcodedRateAndScalesToFourDecimals(String amount, String currency, String expected) {
            BigDecimal result = converter.convertToUsd(new BigDecimal(amount), currency);

            assertEquals(new BigDecimal(expected), result);
            assertEquals(4, result.scale());
        }

        @Test
        void returnsTheSameInstanceForUsd() {
            BigDecimal amount = new BigDecimal("42.42");

            assertSame(amount, converter.convertToUsd(amount, "USD"));
        }

        @Test
        void isCaseInsensitiveOnTheCurrencyCode() {
            assertEquals(new BigDecimal("108.7500"), converter.convertToUsd(new BigDecimal("100.00"), "eur"));
        }

        @Test
        void roundsHalfUpToFourDecimals() {
            // 0.015 JPY * 0.0071 = 0.00010650 -> HALF_UP at scale 4 = 0.0001
            assertEquals(new BigDecimal("0.0001"), converter.convertToUsd(new BigDecimal("0.015"), "JPY"));
        }

        @Test
        void handlesZeroAndNegativeAmounts() {
            assertEquals(new BigDecimal("0.0000"), converter.convertToUsd(BigDecimal.ZERO, "EUR"));
            assertEquals(new BigDecimal("-108.7500"), converter.convertToUsd(new BigDecimal("-100.00"), "EUR"));
        }

        @Test
        void returnsNullForUnsupportedCurrency() {
            assertNull(converter.convertToUsd(new BigDecimal("100.00"), "XYZ"));
        }

        @Test
        void returnsNullWhenAmountOrCurrencyIsNull() {
            assertNull(converter.convertToUsd(null, "EUR"));
            assertNull(converter.convertToUsd(new BigDecimal("100.00"), null));
            assertNull(converter.convertToUsd(null, null));
        }
    }

    @Nested
    class ConvertFromUsd {

        @Test
        void dividesByTheRateAndScalesToFourDecimals() {
            // 108.75 USD / 1.0875 = 100.0000 EUR
            BigDecimal result = converter.convertFromUsd(new BigDecimal("108.75"), "EUR");

            assertEquals(new BigDecimal("100.0000"), result);
            assertEquals(4, result.scale());
        }

        @Test
        void returnsTheSameInstanceForUsd() {
            BigDecimal amount = new BigDecimal("42.42");

            assertSame(amount, converter.convertFromUsd(amount, "usd"));
        }

        @Test
        void roundsRepeatingResultsHalfUp() {
            // 1 USD / 0.0071 = 140.845070... -> 140.8451
            assertEquals(new BigDecimal("140.8451"), converter.convertFromUsd(BigDecimal.ONE, "JPY"));
        }

        @Test
        void returnsNullForUnsupportedCurrency() {
            assertNull(converter.convertFromUsd(new BigDecimal("100.00"), "XYZ"));
        }

        @Test
        void returnsNullWhenAmountOrCurrencyIsNull() {
            assertNull(converter.convertFromUsd(null, "EUR"));
            assertNull(converter.convertFromUsd(new BigDecimal("100.00"), null));
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
        @ValueSource(strings = {"XYZ", "", "US", "USDD"})
        void returnsFalseForUnknownCurrencies(String currency) {
            assertFalse(converter.isSupported(currency));
        }

        @Test
        void returnsFalseForNull() {
            assertFalse(converter.isSupported(null));
        }
    }
}
