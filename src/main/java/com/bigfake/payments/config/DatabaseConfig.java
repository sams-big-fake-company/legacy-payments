package com.bigfake.payments.config;

import org.springframework.context.annotation.Configuration;

/**
 * Database configuration.
 *
 * <p>There is intentionally no explicit {@code DataSource} bean here. Every profile configures its
 * datasource through {@code spring.datasource.*} in {@code application.yml} (H2) and
 * {@code application-prod.yml} (PostgreSQL), so Spring Boot's {@code DataSourceAutoConfiguration}
 * builds a pooled HikariCP datasource and Flyway/JPA consume it. Declaring a {@code DataSource}
 * bean here makes that auto-configuration back off and silently discards the
 * {@code spring.datasource.hikari.*} pool settings.
 *
 * TODO: PAY-3100 - Connection pooling should use HikariCP settings properly
 * TODO: PAY-3101 - Add read replica support for reporting queries
 */
@Configuration
public class DatabaseConfig {
}
