package com.bigfake.payments.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the bean definitions in the configuration classes. No Spring context is
 * started - the configuration classes are instantiated directly.
 */
class ConfigurationBeansTest {

    @Test
    void swaggerConfig_exposesApiMetadata() {
        OpenAPI openAPI = new SwaggerConfig().customOpenAPI();

        assertNotNull(openAPI.getInfo());
        assertEquals("Legacy Payments API", openAPI.getInfo().getTitle());
        assertEquals("1.4.2", openAPI.getInfo().getVersion());
        assertEquals("payments-platform@bigfake.com", openAPI.getInfo().getContact().getEmail());
        assertEquals("Internal Use Only", openAPI.getInfo().getLicense().getName());
    }

    @Test
    void databaseConfig_prodDataSourceUsesPostgresDriver() {
        DataSource dataSource = new DatabaseConfig().prodDataSource();

        assertTrue(dataSource instanceof DriverManagerDataSource);
        DriverManagerDataSource driverManagerDataSource = (DriverManagerDataSource) dataSource;
        assertEquals("jdbc:postgresql://payments-db.internal:5432/payments", driverManagerDataSource.getUrl());
        assertEquals("payments_app", driverManagerDataSource.getUsername());
    }

    @Test
    void securityConfig_passwordEncoderHashesAndMatches() {
        PasswordEncoder encoder = new SecurityConfig().passwordEncoder();

        String hash = encoder.encode("admin123");

        assertFalse(hash.contains("admin123"));
        assertTrue(encoder.matches("admin123", hash));
        assertFalse(encoder.matches("wrong-password", hash));
    }

    @Test
    void securityConfig_registersAdminAndServiceAccounts() {
        SecurityConfig config = new SecurityConfig();
        UserDetailsService userDetailsService = config.userDetailsService();

        UserDetails admin = userDetailsService.loadUserByUsername("admin");
        UserDetails serviceAccount = userDetailsService.loadUserByUsername("payment-gateway");

        assertTrue(admin.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
        assertTrue(serviceAccount.getAuthorities().stream().anyMatch(a -> "ROLE_SERVICE".equals(a.getAuthority())));
    }
}
