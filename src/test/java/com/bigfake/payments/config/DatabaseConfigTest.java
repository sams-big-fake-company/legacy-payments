package com.bigfake.payments.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
            .withUserConfiguration(DatabaseConfig.class)
            .withPropertyValues(
                    "spring.datasource.url=jdbc:postgresql://payments-db.internal:5432/payments",
                    "spring.datasource.username=payments_app",
                    "spring.datasource.password=secret",
                    "spring.datasource.driver-class-name=org.postgresql.Driver",
                    "spring.datasource.hikari.maximum-pool-size=20",
                    "spring.datasource.hikari.minimum-idle=5",
                    "spring.datasource.hikari.connection-timeout=30000");

    @Test
    void prodDataSourceIsPooledAndConfiguredFromProperties() {
        contextRunner.withPropertyValues("spring.profiles.active=prod").run(context -> {
            assertThat(context).hasSingleBean(DataSource.class);
            assertThat(context.getBean(DataSource.class)).isInstanceOf(HikariDataSource.class);
            HikariDataSource dataSource = (HikariDataSource) context.getBean(DataSource.class);
            assertThat(dataSource.getJdbcUrl()).isEqualTo("jdbc:postgresql://payments-db.internal:5432/payments");
            assertThat(dataSource.getUsername()).isEqualTo("payments_app");
            assertThat(dataSource.getPassword()).isEqualTo("secret");
            assertThat(dataSource.getMaximumPoolSize()).isEqualTo(20);
            assertThat(dataSource.getMinimumIdle()).isEqualTo(5);
            assertThat(dataSource.getConnectionTimeout()).isEqualTo(30000);
        });
    }

    @Test
    void prodDataSourceIsNotRegisteredOutsideProdProfile() {
        contextRunner.run(context ->
                assertThat(context).doesNotHaveBean("prodDataSource"));
    }
}
