package com.temporalrift.game.session.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;

import com.temporalrift.events.envelope.EventEnvelope;

/**
 * Configures Spring Modulith to externalize {@link EventEnvelope} instances to the
 * {@code game.events} Kafka topic, partitioned by {@code gameId} to guarantee in-game ordering.
 *
 * <p>See <a href="https://github.com/temporal-rift/game-service/issues/14">issue #14</a> — move to
 * {@code shared/} when {@code action} or {@code scoring} modules start publishing events.
 */
@Configuration
class KafkaExternalizationConfig {

    @Bean
    EventExternalizationConfiguration eventExternalizationConfiguration() {
        return EventExternalizationConfiguration.externalizing()
                .select(event -> event instanceof EventEnvelope)
                .route(
                        EventEnvelope.class,
                        envelope -> RoutingTarget.forTarget("game.events")
                                .andKey(envelope.gameId().toString()))
                .build();
    }
}
