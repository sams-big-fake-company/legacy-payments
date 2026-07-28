package com.bigfake.payments.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the configuration beans. These instantiate the @Configuration
 * classes directly rather than starting a Spring context.
 */
class ConfigurationBeansTest {

    @Test
    void openApiBeanDescribesTheLegacyPaymentsApi() {
        OpenAPI openAPI = new SwaggerConfig().customOpenAPI();

        assertNotNull(openAPI.getInfo());
        assertEquals("Legacy Payments API", openAPI.getInfo().getTitle());
        assertEquals("1.4.2", openAPI.getInfo().getVersion());
        assertTrue(openAPI.getInfo().getDescription().contains("maintenance mode"));
        assertEquals("Payments Platform Team", openAPI.getInfo().getContact().getName());
        assertEquals("payments-platform@bigfake.com", openAPI.getInfo().getContact().getEmail());
        assertEquals("Internal Use Only", openAPI.getInfo().getLicense().getName());
    }

    @Test
    void prodDataSourceUsesThePostgresDriverAndInternalHost() {
        DataSource dataSource = new DatabaseConfig().prodDataSource();

        assertTrue(dataSource instanceof DriverManagerDataSource);
        DriverManagerDataSource driverDataSource = (DriverManagerDataSource) dataSource;
        assertEquals("jdbc:postgresql://payments-db.internal:5432/payments", driverDataSource.getUrl());
        assertEquals("payments_app", driverDataSource.getUsername());
    }
}
