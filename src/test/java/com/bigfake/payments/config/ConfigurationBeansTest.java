package com.bigfake.payments.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the standalone beans declared by the configuration classes.
 */
class ConfigurationBeansTest {

    @Test
    void openApiDocumentDescribesTheService() {
        OpenAPI openApi = new SwaggerConfig().customOpenAPI();

        assertEquals("Legacy Payments API", openApi.getInfo().getTitle());
        assertEquals("1.4.2", openApi.getInfo().getVersion());
        assertEquals("payments-platform@bigfake.com", openApi.getInfo().getContact().getEmail());
        assertEquals("Internal Use Only", openApi.getInfo().getLicense().getName());
    }

    @Test
    void productionDataSourcePointsAtThePostgresInstance() {
        DataSource dataSource = new DatabaseConfig().prodDataSource();

        assertTrue(dataSource instanceof DriverManagerDataSource);
        DriverManagerDataSource driverManagerDataSource = (DriverManagerDataSource) dataSource;
        assertEquals("jdbc:postgresql://payments-db.internal:5432/payments",
                driverManagerDataSource.getUrl());
        assertEquals("payments_app", driverManagerDataSource.getUsername());
    }
}
