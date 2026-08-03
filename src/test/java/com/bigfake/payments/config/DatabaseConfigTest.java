package com.bigfake.payments.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Unit tests for DatabaseConfig.
 */
class DatabaseConfigTest {

    @Test
    void prodDataSourcePointsAtThePostgresPaymentsDatabase() {
        DataSource dataSource = new DatabaseConfig().prodDataSource();

        DriverManagerDataSource driverDataSource = assertInstanceOf(DriverManagerDataSource.class, dataSource);
        assertEquals("jdbc:postgresql://payments-db.internal:5432/payments", driverDataSource.getUrl());
        assertEquals("payments_app", driverDataSource.getUsername());
    }
}
