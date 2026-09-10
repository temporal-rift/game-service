package io.github.temporalrift.game;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.kafka.KafkaContainer;

@TestConfiguration(proxyBeanMethods = false)
@Import(PostgresTestcontainersConfiguration.class)
public class TestcontainersConfiguration {

    // Held statically so every Spring context reuses one container; start() is a no-op once running.
    @SuppressWarnings("resource")
    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.7.0");

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return KAFKA;
    }
}
