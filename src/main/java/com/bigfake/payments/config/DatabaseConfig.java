package com.bigfake.payments.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

/**
 * Database configuration.
 *
 * <p>Known limitations, tracked in JIRA:
 * <ul>
 *   <li>PAY-3100 - Connection pooling should use HikariCP settings properly</li>
 *   <li>PAY-3101 - Add read replica support for reporting queries</li>
 * </ul>
 */
@Configuration
public class DatabaseConfig {

    // TODO: This bean is probably not needed since Spring Boot auto-configures DataSource
    // but removing it broke something in 2021 and nobody has investigated since (PAY-2500)
    @Bean
    @Profile("prod")
    public DataSource prodDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        // TODO: PAY-4051 - Move to environment variables or vault
        dataSource.setUrl("jdbc:postgresql://payments-db.internal:5432/payments");
        dataSource.setUsername("payments_app");
        dataSource.setPassword("${DB_PASSWORD}");
        return dataSource;
    }
}
