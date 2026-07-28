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
 * Unit tests for {@link CurrencyConverter} and its hardcoded rate table.
 */
class CurrencyConverterTest {

    private CurrencyConverter converter;

    @BeforeEach
    void setUp() {
        converter = new CurrencyConverter();
    }

    @ParameterizedTest(name = "{1} {0} converts to {2} USD")
    @CsvSource({
            "100.00, EUR, 108.7500",
            "100.00, GBP, 126.5000",
            "100.00, CAD, 74.5000",
            "100.00, AUD, 65.2000",
            "10000.00, JPY, 71.0000",
            "100.00, CHF, 111.5000",
            "100.00, CNY, 13.8000",
            "100.00, INR, 1.2100",
            "100.00, BRL, 20.5000"
    })
    void convertsSupportedCurrenciesToUsd(String amount, String currency, String expected) {
        assertEquals(new BigDecimal(expected), converter.convertToUsd(new BigDecimal(amount), currency));
    }

    @Test
    void returnsUsdAmountsUnchangedAndKeepsTheirScale() {
        BigDecimal amount = new BigDecimal("42.50");

        assertEquals(amount, converter.convertToUsd(amount, "USD"));
        assertEquals(amount, converter.convertFromUsd(amount, "USD"));
    }

    @ParameterizedTest(name = "currency code \"{0}\" is matched case-insensitively")
    @ValueSource(strings = {"eur", "Eur", "EUR"})
    void currencyCodesAreCaseInsensitive(String currency) {
        assertEquals(new BigDecimal("108.7500"), converter.convertToUsd(new BigDecimal("100.00"), currency));
        assertTrue(converter.isSupported(currency));
    }

    @Test
    void convertsFromUsdToTheTargetCurrency() {
        assertEquals(new BigDecimal("91.9540"), converter.convertFromUsd(new BigDecimal("100.00"), "EUR"));
    }

    @Test
    void roundsHalfUpToFourDecimalPlaces() {
        assertEquals(new BigDecimal("0.0121"), converter.convertToUsd(BigDecimal.ONE, "INR"));
        assertEquals(new BigDecimal("82.6446"), converter.convertFromUsd(BigDecimal.ONE, "INR"));
    }

    @Test
    void returnsNullForUnsupportedCurrencies() {
        assertNull(converter.convertToUsd(BigDecimal.TEN, "XYZ"));
        assertNull(converter.convertFromUsd(BigDecimal.TEN, "XYZ"));
        assertFalse(converter.isSupported("XYZ"));
    }

    @Test
    void returnsNullWhenArgumentsAreMissing() {
        assertNull(converter.convertToUsd(null, "EUR"));
        assertNull(converter.convertToUsd(BigDecimal.TEN, null));
        assertNull(converter.convertFromUsd(null, "EUR"));
        assertNull(converter.convertFromUsd(BigDecimal.TEN, null));
    }

    @Test
    void unknownAndNullCurrenciesAreNotSupported() {
        assertFalse(converter.isSupported(null));
        assertFalse(converter.isSupported(""));
        assertTrue(converter.isSupported("usd"));
    }
}
