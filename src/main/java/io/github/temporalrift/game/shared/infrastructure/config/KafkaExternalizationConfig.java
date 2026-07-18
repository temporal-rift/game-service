package io.github.temporalrift.game.shared.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;

/**
 * Configures Spring Modulith to externalize the ZenWave-generated producers' {@link Message} events to
 * Kafka, keyed by {@code gameId} to preserve per-game partition ordering.
 *
 * <p>Every generated producer stamps its own Spring Cloud Stream binding name into the {@code
 * spring.cloud.stream.sendto.destination} header. That header is only used here to <em>select</em> our
 * generated messages - it is deliberately not used as the routing destination: game-service publishes
 * every session/action/scoring event to the single {@code game.events} topic (per event-schema.md), and
 * Spring Cloud Stream's {@code destination} binding property cannot be defaulted globally (its default
 * "cannot be overridden" - it always falls back to the binding name), so routing to the binding name
 * would send each event to a topic literally named after the binding. Routing to the fixed {@code
 * game.events} target here is the correct, single place that decision belongs.
 *
 * <p>Not {@code @EnableSpringCloudStreamEventExternalization}: that annotation's built-in configuration
 * routes without a partition key, which would drop the gameId-based ordering guarantee this design
 * relies on.
 */
@Configuration
class KafkaExternalizationConfig {

    private static final String SCS_DESTINATION_HEADER = "spring.cloud.stream.sendto.destination";
    private static final String GAME_EVENTS_TOPIC = "game.events";

    @Bean
    EventExternalizationConfiguration eventExternalizationConfiguration() {
        return EventExternalizationConfiguration.externalizing()
                .select(KafkaExternalizationConfig::isExternalizable)
                .route(
                        Message.class,
                        message -> RoutingTarget.forTarget(GAME_EVENTS_TOPIC).andKey(gameId(message)))
                .build();
    }

    private static boolean isExternalizable(Object event) {
        return event instanceof Message<?> message && scsDestination(message) != null;
    }

    private static String scsDestination(Message<?> message) {
        return message.getHeaders().get(SCS_DESTINATION_HEADER, String.class);
    }

    private static String gameId(Message<?> message) {
        var gameId = message.getHeaders().get("gameId", String.class);
        if (gameId == null) {
            throw new IllegalStateException("Message is missing the required 'gameId' header: " + message);
        }
        return gameId;
    }
}
