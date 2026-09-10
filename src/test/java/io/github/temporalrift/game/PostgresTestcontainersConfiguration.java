package io.github.temporalrift.game;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The persistence slice only, which is safe to share: it starts no listeners and no schedulers, and every
 * test rolls back. Full application contexts get their own pair from {@link TestcontainersConfiguration}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainersConfiguration {

    // Held statically so every persistence context reuses one container; start() is a no-op once running.
    @SuppressWarnings("resource")
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("temporal_rift")
            .withUsername("temporal_rift")
            .withPassword("temporal_rift");

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return POSTGRES;
    }
}
