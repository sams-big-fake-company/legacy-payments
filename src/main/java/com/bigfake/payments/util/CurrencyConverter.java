package com.bigfake.payments.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * Currency conversion utility.
 *
 * WARNING: This uses HARDCODED exchange rates!
 * TODO: PAY-2876 - Integrate with a real-time exchange rate API (e.g., Open Exchange Rates)
 * TODO: PAY-2877 - Cache rates with appropriate TTL
 * TODO: PAY-2878 - Handle rate staleness detection
 *
 * Last updated rates: 2023-06-15 (YES, these are stale)
 *
 * @deprecated This entire class should be replaced with a proper FX service
 */
@Deprecated
@Component
public class CurrencyConverter {

    private static final Logger log = LoggerFactory.getLogger(CurrencyConverter.class);

    // Hardcoded rates to USD - obviously terrible practice
    // TODO: PAY-2876 - Replace with real-time rates
    private static final Map<String, BigDecimal> RATES_TO_USD = new HashMap<>();

    static {
        RATES_TO_USD.put("USD", BigDecimal.ONE);
        RATES_TO_USD.put("EUR", new BigDecimal("1.0875"));   // 1 EUR = 1.0875 USD
        RATES_TO_USD.put("GBP", new BigDecimal("1.2650"));   // 1 GBP = 1.2650 USD
        RATES_TO_USD.put("CAD", new BigDecimal("0.7450"));   // 1 CAD = 0.7450 USD
        RATES_TO_USD.put("AUD", new BigDecimal("0.6520"));   // 1 AUD = 0.6520 USD
        RATES_TO_USD.put("JPY", new BigDecimal("0.0071"));   // 1 JPY = 0.0071 USD
        RATES_TO_USD.put("CHF", new BigDecimal("1.1150"));   // 1 CHF = 1.1150 USD
        RATES_TO_USD.put("CNY", new BigDecimal("0.1380"));   // 1 CNY = 0.1380 USD
        RATES_TO_USD.put("INR", new BigDecimal("0.0121"));   // 1 INR = 0.0121 USD
        RATES_TO_USD.put("BRL", new BigDecimal("0.2050"));   // 1 BRL = 0.2050 USD
    }

    /**
     * Convert an amount from a given currency to USD.
     *
     * @param amount the amount to convert
     * @param fromCurrency the source currency code (ISO 4217)
     * @return the amount in USD, or null if currency not supported
     */
    public BigDecimal convertToUsd(BigDecimal amount, String fromCurrency) {
        if (amount == null || fromCurrency == null) {
            return null;
        }

        String currency = fromCurrency.toUpperCase();
        BigDecimal rate = RATES_TO_USD.get(currency);

        if (rate == null) {
            log.warn("Unsupported currency for conversion: {}", fromCurrency);
            return null;
        }

        if ("USD".equals(currency)) {
            return amount;
        }

        return amount.multiply(rate).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Convert an amount from USD to a target currency.
     *
     * @param amountUsd the amount in USD
     * @param toCurrency the target currency code
     * @return the converted amount, or null if currency not supported
     */
    public BigDecimal convertFromUsd(BigDecimal amountUsd, String toCurrency) {
        if (amountUsd == null || toCurrency == null) {
            return null;
        }

        String currency = toCurrency.toUpperCase();
        BigDecimal rate = RATES_TO_USD.get(currency);

        if (rate == null) {
            log.warn("Unsupported currency for conversion: {}", toCurrency);
            return null;
        }

        if ("USD".equals(currency)) {
            return amountUsd;
        }

        return amountUsd.divide(rate, 4, RoundingMode.HALF_UP);
    }

    /**
     * Check if a currency is supported for conversion.
     */
    public boolean isSupported(String currency) {
        return currency != null && RATES_TO_USD.containsKey(currency.toUpperCase());
    }
}
