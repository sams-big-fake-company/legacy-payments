package com.bigfake.payments.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;

/**
 * Database configuration.
 *
 * The primary connection pool is HikariCP, configured through
 * {@code spring.datasource.hikari.*}. When {@code spring.datasource.replica.url} is set,
 * read-only transactions are routed to a second pool configured through
 * {@code spring.datasource.replica.*} (and {@code spring.datasource.replica.hikari.*}).
 */
@Configuration
public class DatabaseConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties primaryDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource primaryDataSource(
            @Qualifier("primaryDataSourceProperties") DataSourceProperties primaryDataSourceProperties) {
        return primaryDataSourceProperties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Configuration
    @ConditionalOnProperty("spring.datasource.replica.url")
    public static class ReadReplicaConfiguration {

        @Bean
        @ConfigurationProperties("spring.datasource.replica")
        public DataSourceProperties replicaDataSourceProperties() {
            return new DataSourceProperties();
        }

        @Bean
        @ConfigurationProperties("spring.datasource.replica.hikari")
        public HikariDataSource replicaDataSource(
                @Qualifier("replicaDataSourceProperties") DataSourceProperties replicaDataSourceProperties) {
            return replicaDataSourceProperties.initializeDataSourceBuilder()
                    .type(HikariDataSource.class)
                    .build();
        }

        /**
         * Lazy proxy so the target pool is chosen once the transaction's read-only flag is known.
         */
        @Bean
        @Primary
        public DataSource dataSource(@Qualifier("primaryDataSource") HikariDataSource primaryDataSource,
                                    @Qualifier("replicaDataSource") HikariDataSource replicaDataSource) {
            return new LazyConnectionDataSourceProxy(
                    new ReadWriteRoutingDataSource(primaryDataSource, replicaDataSource));
        }
    }
}
