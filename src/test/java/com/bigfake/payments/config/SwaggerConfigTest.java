package com.bigfake.payments.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for SwaggerConfig.
 */
class SwaggerConfigTest {

    @Test
    void publishesTheApiMetadataUsedBySwaggerUi() {
        OpenAPI openApi = new SwaggerConfig().customOpenAPI();

        assertNotNull(openApi.getInfo());
        assertEquals("Legacy Payments API", openApi.getInfo().getTitle());
        assertEquals("1.4.2", openApi.getInfo().getVersion());
        assertEquals("Payments Platform Team", openApi.getInfo().getContact().getName());
        assertEquals("payments-platform@bigfake.com", openApi.getInfo().getContact().getEmail());
        assertEquals("Internal Use Only", openApi.getInfo().getLicense().getName());
    }
}
