package io.github.temporalrift.game;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Deliberately one container pair per context, not the shared static instance
 * {@link PostgresTestcontainersConfiguration} hands the persistence slice. A full application context keeps
 * listeners and schedulers running for the rest of the JVM once it is cached, so two of them on one broker
 * put two members in every consumer group — Kafka hands each partition to only one, routing a test's own
 * events to the other context's listeners — and two on one database run two event-publication resubmitters
 * over the same rows.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    @SuppressWarnings("resource")
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:16-alpine")
                .withDatabaseName("temporal_rift")
                .withUsername("temporal_rift")
                .withPassword("temporal_rift");
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer("apache/kafka:3.7.0");
    }
}
