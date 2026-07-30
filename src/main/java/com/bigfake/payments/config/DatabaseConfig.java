package com.bigfake.payments.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

/**
 * Database configuration.
 *
 * <p>Known follow-up work tracked outside this class:
 * <ul>
 *   <li>PAY-3100: the prod {@link DataSource} below bypasses Spring Boot's HikariCP
 *       auto-configuration, so the {@code spring.datasource.hikari.*} settings in
 *       {@code application-prod.yml} are not applied. Switching to a pooled
 *       {@code DataSource} changes prod connection behaviour and must be rolled out
 *       and load-tested under PAY-3100.</li>
 *   <li>PAY-3101: read replica support for reporting queries requires a second
 *       datasource plus routing at the repository layer; tracked under PAY-3101.</li>
 *   <li>PAY-2500: this bean is most likely redundant with Spring Boot's DataSource
 *       auto-configuration, but removing it caused an incident in 2021 and the
 *       investigation is still open under PAY-2500.</li>
 * </ul>
 */
@Configuration
public class DatabaseConfig {

    @Value("${spring.datasource.url:jdbc:postgresql://payments-db.internal:5432/payments}")
    private String prodUrl;

    @Value("${spring.datasource.username:payments_app}")
    private String prodUsername;

    @Value("${spring.datasource.password:}")
    private String prodPassword;

    @Bean
    @Profile("prod")
    public DataSource prodDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(prodUrl);
        dataSource.setUsername(prodUsername);
        dataSource.setPassword(prodPassword);
        return dataSource;
    }
}
