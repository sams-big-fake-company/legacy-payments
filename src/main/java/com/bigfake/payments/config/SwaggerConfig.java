package com.bigfake.payments.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI configuration.
 *
 * Access docs at: /swagger-ui.html
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Legacy Payments API")
                        .version("1.4.2")
                        .description("Payment processing API for BigFake Company. " +
                                "NOTE: This API is in maintenance mode. " +
                                "New integrations should use the v2 gateway service.")
                        .contact(new Contact()
                                .name("Payments Platform Team")
                                .email("payments-platform@bigfake.com"))
                        .license(new License()
                                .name("Internal Use Only")));
    }
}
