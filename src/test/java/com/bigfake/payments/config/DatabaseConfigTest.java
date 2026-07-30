package com.bigfake.payments.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(DatabaseConfig.class)
            .withPropertyValues(
                    "spring.datasource.url=jdbc:h2:mem:primary",
                    "spring.datasource.driver-class-name=org.h2.Driver",
                    "spring.datasource.hikari.maximum-pool-size=7");

    @Test
    void usesSingleHikariPoolWhenNoReplicaConfigured() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(DataSource.class);
            HikariDataSource dataSource = context.getBean(HikariDataSource.class);
            assertThat(dataSource.getMaximumPoolSize()).isEqualTo(7);
            assertThat(dataSource.getJdbcUrl()).isEqualTo("jdbc:h2:mem:primary");
        });
    }

    @Test
    void routesTransactionsByReadOnlyFlagWhenReplicaConfigured() {
        contextRunner
                .withPropertyValues(
                        "spring.datasource.replica.url=jdbc:h2:mem:replica",
                        "spring.datasource.replica.driver-class-name=org.h2.Driver",
                        "spring.datasource.replica.hikari.maximum-pool-size=3")
                .run(context -> {
                    assertThat(context.getBean("dataSource")).isInstanceOf(LazyConnectionDataSourceProxy.class);
                    HikariDataSource primary = context.getBean("primaryDataSource", HikariDataSource.class);
                    HikariDataSource replica = context.getBean("replicaDataSource", HikariDataSource.class);
                    assertThat(replica.getJdbcUrl()).isEqualTo("jdbc:h2:mem:replica");
                    assertThat(replica.getMaximumPoolSize()).isEqualTo(3);

                    ReadWriteRoutingDataSource routing = new ReadWriteRoutingDataSource(primary, replica);
                    assertThat(jdbcUrlOf(routing)).isEqualTo("jdbc:h2:mem:primary");

                    TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
                    try {
                        assertThat(jdbcUrlOf(routing)).isEqualTo("jdbc:h2:mem:replica");
                    } finally {
                        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
                    }
                });
    }

    private static String jdbcUrlOf(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getURL();
        }
    }
}
