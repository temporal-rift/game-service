package io.github.temporalrift.game.shared.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;

import io.github.temporalrift.events.envelope.EventEnvelope;

/**
 * Configures Spring Modulith to externalize {@link EventEnvelope} instances to the
 * {@code game.events} Kafka topic, partitioned by {@code gameId} to guarantee in-game ordering.
 */
@Configuration
class KafkaExternalizationConfig {

    @Bean
    EventExternalizationConfiguration eventExternalizationConfiguration() {
        return EventExternalizationConfiguration.externalizing()
                .select(EventEnvelope.class::isInstance)
                .route(
                        EventEnvelope.class,
                        envelope -> RoutingTarget.forTarget("game.events")
                                .andKey(envelope.gameId().toString()))
                .build();
    }
}
