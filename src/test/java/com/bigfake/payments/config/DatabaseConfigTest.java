package com.bigfake.payments.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the production DataSource definition.
 * No connection is opened - only the configured coordinates are asserted.
 */
class DatabaseConfigTest {

    @Test
    void prodDataSource_isConfiguredForPostgres() {
        DataSource dataSource = new DatabaseConfig().prodDataSource();

        assertTrue(dataSource instanceof DriverManagerDataSource);
        DriverManagerDataSource driverDataSource = (DriverManagerDataSource) dataSource;
        assertEquals("jdbc:postgresql://payments-db.internal:5432/payments", driverDataSource.getUrl());
        assertEquals("payments_app", driverDataSource.getUsername());
    }
}
