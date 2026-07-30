package com.bigfake.payments.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Routes connections of read-only transactions to a read replica and everything else
 * to the primary database.
 */
public class ReadWriteRoutingDataSource extends AbstractRoutingDataSource {

    enum Target {
        PRIMARY,
        REPLICA
    }

    public ReadWriteRoutingDataSource(DataSource primary, DataSource replica) {
        Map<Object, Object> targets = new HashMap<>();
        targets.put(Target.PRIMARY, primary);
        targets.put(Target.REPLICA, replica);
        setTargetDataSources(targets);
        setDefaultTargetDataSource(primary);
        afterPropertiesSet();
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                ? Target.REPLICA
                : Target.PRIMARY;
    }
}
