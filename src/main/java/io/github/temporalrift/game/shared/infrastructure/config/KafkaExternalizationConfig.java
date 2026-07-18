package io.github.temporalrift.game.shared.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;

import io.github.temporalrift.events.envelope.EventEnvelope;

/**
 * Configures Spring Modulith to externalize both representations event publishers can currently
 * produce, during the migration from {@link EventEnvelope} (domain-events) to the generated ZenWave
 * producers' {@link Message}: EventsDrawn, HandDealt and BandedProbabilityPublished are blocked on
 * apis PR #4 (temporal-rift/apis) being merged and published to Maven Central, so those three still
 * publish {@link EventEnvelope} directly in the meantime. An object is only ever one or the other,
 * never both, so supporting both selectors here does not risk double-publishing. Drop the
 * EventEnvelope branch once PR #4 publishes and those three events are wired to their generated
 * producers, and domain-events is removed entirely.
 */
@Configuration
class KafkaExternalizationConfig {

    private static final String SCS_DESTINATION_HEADER = "spring.cloud.stream.sendto.destination";

    @Bean
    EventExternalizationConfiguration eventExternalizationConfiguration() {
        return EventExternalizationConfiguration.externalizing()
                .select(KafkaExternalizationConfig::isExternalizable)
                .route(
                        EventEnvelope.class,
                        envelope -> RoutingTarget.forTarget("game.events")
                                .andKey(envelope.gameId().toString()))
                .route(
                        Message.class,
                        message ->
                                RoutingTarget.forTarget(scsDestination(message)).andKey(gameId(message)))
                .build();
    }

    private static boolean isExternalizable(Object event) {
        return event instanceof EventEnvelope
                || (event instanceof Message<?> message && scsDestination(message) != null);
    }

    private static String scsDestination(Message<?> message) {
        return message.getHeaders().get(SCS_DESTINATION_HEADER, String.class);
    }

    private static String gameId(Message<?> message) {
        return message.getHeaders().get("gameId", String.class);
    }
}
