package com.bigfake.payments.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

/**
 * Database configuration.
 *
 * TODO: PAY-3100 - Connection pooling should use HikariCP settings properly
 */
@Configuration
@EnableConfigurationProperties(ReplicaDataSourceProperties.class)
public class DatabaseConfig {

    static final String REPLICA_DATASOURCE = "replicaDataSource";

    // TODO: This bean is probably not needed since Spring Boot auto-configures DataSource
    // but removing it broke something in 2021 and nobody has investigated since (PAY-2500)
    @Bean
    @Primary
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

    /**
     * Read-only connection pool against the PostgreSQL read replica, used for
     * reporting queries. Injectable with {@code @Qualifier("replicaDataSource")};
     * JPA and everything else keep using the primary datasource.
     */
    @Bean(name = REPLICA_DATASOURCE, destroyMethod = "close")
    @Profile("prod")
    @ConditionalOnProperty(prefix = "payments.datasource.replica", name = "enabled", havingValue = "true")
    public DataSource replicaDataSource(ReplicaDataSourceProperties properties) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("payments-replica");
        config.setDriverClassName(properties.getDriverClassName());
        config.setJdbcUrl(properties.getUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setMaximumPoolSize(properties.getMaximumPoolSize());
        config.setMinimumIdle(properties.getMinimumIdle());
        config.setConnectionTimeout(properties.getConnectionTimeoutMs());
        config.setIdleTimeout(properties.getIdleTimeoutMs());
        config.setMaxLifetime(properties.getMaxLifetimeMs());
        config.setReadOnly(true);
        return new HikariDataSource(config);
    }

    /**
     * JdbcTemplate for reporting / read-only queries. Uses the read replica when
     * one is configured, otherwise falls back to the primary datasource.
     */
    @Bean
    public JdbcTemplate reportingJdbcTemplate(DataSource dataSource,
                                              @Qualifier(REPLICA_DATASOURCE) ObjectProvider<DataSource> replica) {
        return new JdbcTemplate(replica.getIfAvailable(() -> dataSource));
    }
}
