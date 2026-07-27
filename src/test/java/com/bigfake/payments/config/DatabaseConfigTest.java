package com.bigfake.payments.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the prod datasource wiring: {@code application-prod.yml} must fully describe the
 * PostgreSQL datasource so Boot can auto-configure a pooled HikariCP {@code DataSource}.
 */
class DatabaseConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
            .withUserConfiguration(DatabaseConfig.class);

    @Test
    @DisplayName("prod profile datasource is pooled and built from application-prod.yml")
    void prodDataSourceIsAutoConfiguredHikariPool() {
        contextRunner
                .withPropertyValues("spring.profiles.active=prod", "DB_PASSWORD=test-password")
                .run(context -> {
                    DataSource dataSource = context.getBean("dataSource", DataSource.class);
                    assertThat(dataSource).isInstanceOf(HikariDataSource.class);

                    HikariDataSource hikari = (HikariDataSource) dataSource;
                    assertThat(hikari.getJdbcUrl())
                            .isEqualTo("jdbc:postgresql://payments-db.internal:5432/payments");
                    assertThat(hikari.getUsername()).isEqualTo("payments_app");
                    assertThat(hikari.getPassword()).isEqualTo("test-password");
                    assertThat(hikari.getDriverClassName()).isEqualTo("org.postgresql.Driver");
                    assertThat(hikari.getMaximumPoolSize()).isEqualTo(20);
                    assertThat(hikari.getMinimumIdle()).isEqualTo(5);
                });
    }

    @Test
    @DisplayName("prod datasource host, database and credentials are overridable via environment")
    void prodDataSourceHonoursEnvironmentOverrides() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "DB_HOST=db.example.com",
                        "DB_PORT=6432",
                        "DB_NAME=payments_replica",
                        "DB_USERNAME=reporting_app",
                        "DB_PASSWORD=another-password")
                .run(context -> {
                    HikariDataSource hikari = (HikariDataSource) context.getBean("dataSource", DataSource.class);
                    assertThat(hikari.getJdbcUrl())
                            .isEqualTo("jdbc:postgresql://db.example.com:6432/payments_replica");
                    assertThat(hikari.getUsername()).isEqualTo("reporting_app");
                    assertThat(hikari.getPassword()).isEqualTo("another-password");
                });
    }
}
