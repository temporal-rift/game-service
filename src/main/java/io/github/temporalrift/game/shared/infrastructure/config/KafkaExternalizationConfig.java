package io.github.temporalrift.game.shared.infrastructure.config;

import org.springframework.context.annotation.Configuration;

import io.zenwave360.modulith.events.scs.config.EnableSpringCloudStreamEventExternalization;

/**
 * Bridges Spring Modulith's transactional outbox relay to Spring Cloud Stream. Generated ZenWave
 * producers publish a {@link org.springframework.messaging.Message} carrying a
 * {@code spring.cloud.stream.sendto.destination} header naming the functional binding to send on
 * (e.g. {@code publish-lobby-created-out}); the actual topic and partition key come from standard
 * {@code spring.cloud.stream.bindings.*} / {@code spring.cloud.stream.kafka.bindings.*} properties
 * in application.yml, not from code here.
 */
@Configuration
@EnableSpringCloudStreamEventExternalization
class KafkaExternalizationConfig {}
