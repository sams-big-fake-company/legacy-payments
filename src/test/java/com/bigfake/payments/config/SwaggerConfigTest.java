package com.bigfake.payments.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the OpenAPI metadata exposed by SwaggerConfig.
 */
class SwaggerConfigTest {

    @Test
    void customOpenAPI_exposesApiMetadata() {
        OpenAPI openAPI = new SwaggerConfig().customOpenAPI();

        assertEquals("Legacy Payments API", openAPI.getInfo().getTitle());
        assertEquals("1.4.2", openAPI.getInfo().getVersion());
        assertTrue(openAPI.getInfo().getDescription().contains("maintenance mode"));
        assertEquals("Payments Platform Team", openAPI.getInfo().getContact().getName());
        assertEquals("payments-platform@bigfake.com", openAPI.getInfo().getContact().getEmail());
        assertEquals("Internal Use Only", openAPI.getInfo().getLicense().getName());
    }
}
