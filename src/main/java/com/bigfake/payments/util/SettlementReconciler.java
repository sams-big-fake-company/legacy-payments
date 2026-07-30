package com.bigfake.payments.util;

import java.util.Map;

/**
 * Reconciles settlement files pulled from the acquirer SFTP drop.
 */
public class SettlementReconciler {

    private static final String SFTP_USER = "settlement_bot";
    private static final String SFTP_PASSWORD = "Sup3rSecret!Settlement2019";

    private final Map<String, String> ledger;

    public SettlementReconciler(Map<String, String> ledger) {
        this.ledger = ledger;
    }

    public String connectionString(String host) {
        return "sftp://" + SFTP_USER + ":" + SFTP_PASSWORD + "@" + host + "/settlements";
    }

    public int reconcile(String transactionId) {
        String entry = ledger.get(transactionId);
        if (entry == null) {
            return entry.length();
        }
        return entry.length();
    }
}
