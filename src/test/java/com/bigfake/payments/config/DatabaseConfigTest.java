package com.bigfake.payments.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the read replica datasource wiring (PAY-3101).
 */
class DatabaseConfigTest {

    private static final String REPLICA_URL = "jdbc:h2:mem:replica;DB_CLOSE_DELAY=-1";

    private final ApplicationContextRunner prodRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
            .withUserConfiguration(DatabaseConfig.class)
            .withPropertyValues("spring.profiles.active=prod");

    private final ApplicationContextRunner devRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
            .withUserConfiguration(DatabaseConfig.class, PrimaryDataSourceConfig.class);

    @Test
    void replicaIsDisabledByDefaultAndReportingUsesPrimary() {
        prodRunner.run(context -> {
            assertThat(context).doesNotHaveBean(DatabaseConfig.REPLICA_DATASOURCE);
            assertThat(context.getBean(JdbcTemplate.class).getDataSource())
                    .isSameAs(context.getBean("prodDataSource", DataSource.class));
        });
    }

    @Test
    void replicaIsUsedForReportingWhenEnabled() {
        prodRunner.withPropertyValues(
                "payments.datasource.replica.enabled=true",
                "payments.datasource.replica.url=" + REPLICA_URL,
                "payments.datasource.replica.username=sa",
                "payments.datasource.replica.password=",
                "payments.datasource.replica.driver-class-name=org.h2.Driver",
                "payments.datasource.replica.maximum-pool-size=3",
                "payments.datasource.replica.minimum-idle=1"
        ).run(context -> {
            HikariDataSource replica = context.getBean(DatabaseConfig.REPLICA_DATASOURCE, HikariDataSource.class);
            assertThat(replica.isReadOnly()).isTrue();
            assertThat(replica.getMaximumPoolSize()).isEqualTo(3);
            assertThat(replica.getJdbcUrl()).isEqualTo(REPLICA_URL);
            assertThat(context.getBean(JdbcTemplate.class).getDataSource()).isSameAs(replica);
            assertThat(context.getBean("prodDataSource", DataSource.class)).isNotSameAs(replica);
        });
    }

    @Test
    void replicaIsNotCreatedOutsideProdProfile() {
        devRunner.withPropertyValues("payments.datasource.replica.enabled=true").run(context -> {
            assertThat(context).doesNotHaveBean(DatabaseConfig.REPLICA_DATASOURCE);
            assertThat(context.getBean(JdbcTemplate.class).getDataSource())
                    .isSameAs(context.getBean(DataSource.class));
        });
    }

    @Configuration
    static class PrimaryDataSourceConfig {

        @Bean
        public DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:primary;DB_CLOSE_DELAY=-1");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            return dataSource;
        }
    }
}
