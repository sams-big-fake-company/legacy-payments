package com.bigfake.payments.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the production DataSource definition.
 */
class DatabaseConfigTest {

    @Test
    void prodDataSource_usesPostgresDriverAndAppUser() {
        DriverManagerDataSource dataSource = (DriverManagerDataSource) new DatabaseConfig().prodDataSource();

        assertTrue(dataSource.getUrl().startsWith("jdbc:postgresql://"));
        assertEquals("payments_app", dataSource.getUsername());
    }
}
