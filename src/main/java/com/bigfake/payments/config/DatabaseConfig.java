package com.bigfake.payments.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

/**
 * Database configuration.
 *
 * TODO: PAY-3101 - Add read replica support for reporting queries
 */
@Configuration
public class DatabaseConfig {

    // TODO: This bean is probably not needed since Spring Boot auto-configures DataSource
    // but removing it broke something in 2021 and nobody has investigated since (PAY-2500)
    @Bean
    @Profile("prod")
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource prodDataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }
}
