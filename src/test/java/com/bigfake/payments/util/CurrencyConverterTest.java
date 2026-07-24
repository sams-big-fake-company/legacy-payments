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

        @Test
        void returnsAmountUnchangedForUsd() {
            BigDecimal amount = new BigDecimal("125.50");

            assertEquals(amount, converter.convertToUsd(amount, "USD"));
        }

        @ParameterizedTest
        @CsvSource({
                "EUR, 100.00, 108.7500",
                "GBP, 100.00, 126.5000",
                "CAD, 100.00, 74.5000",
                "AUD, 100.00, 65.2000",
                "JPY, 10000.00, 71.0000",
                "CHF, 100.00, 111.5000",
                "CNY, 100.00, 13.8000",
                "INR, 1000.00, 12.1000",
                "BRL, 100.00, 20.5000"
        })
        void appliesConfiguredRate(String currency, String amount, String expected) {
            assertEquals(new BigDecimal(expected), converter.convertToUsd(new BigDecimal(amount), currency));
        }

        @Test
        void isCaseInsensitive() {
            assertEquals(converter.convertToUsd(new BigDecimal("50.00"), "EUR"),
                    converter.convertToUsd(new BigDecimal("50.00"), "eur"));
        }

        @Test
        void roundsHalfUpToFourDecimalPlaces() {
            // 33.33333 * 0.0071 = 0.236666... -> 0.2367
            assertEquals(new BigDecimal("0.2367"), converter.convertToUsd(new BigDecimal("33.33333"), "JPY"));
        }

        @Test
        void returnsNullForUnsupportedCurrency() {
            assertNull(converter.convertToUsd(BigDecimal.TEN, "XYZ"));
        }

        @Test
        void returnsNullForNullAmount() {
            assertNull(converter.convertToUsd(null, "EUR"));
        }

        @Test
        void returnsNullForNullCurrency() {
            assertNull(converter.convertToUsd(BigDecimal.TEN, null));
        }

        @Test
        void convertsZeroAmount() {
            assertEquals(new BigDecimal("0.0000"), converter.convertToUsd(BigDecimal.ZERO, "EUR"));
        }
    }

    @Nested
    class ConvertFromUsd {

        @Test
        void returnsAmountUnchangedForUsd() {
            BigDecimal amount = new BigDecimal("99.99");

            assertEquals(amount, converter.convertFromUsd(amount, "USD"));
        }

        @Test
        void dividesByConfiguredRate() {
            // 108.75 / 1.0875 = 100.0000
            assertEquals(new BigDecimal("100.0000"), converter.convertFromUsd(new BigDecimal("108.75"), "EUR"));
        }

        @Test
        void isCaseInsensitive() {
            assertEquals(new BigDecimal("100.0000"), converter.convertFromUsd(new BigDecimal("126.50"), "gbp"));
        }

        @Test
        void returnsNullForUnsupportedCurrency() {
            assertNull(converter.convertFromUsd(BigDecimal.TEN, "ZZZ"));
        }

        @Test
        void returnsNullForNullAmount() {
            assertNull(converter.convertFromUsd(null, "EUR"));
        }

        @Test
        void returnsNullForNullCurrency() {
            assertNull(converter.convertFromUsd(BigDecimal.TEN, null));
        }
    }

    @Nested
    class IsSupported {

        @ParameterizedTest
        @ValueSource(strings = {"USD", "EUR", "GBP", "CAD", "AUD", "JPY", "CHF", "CNY", "INR", "BRL", "usd", "eUr"})
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
