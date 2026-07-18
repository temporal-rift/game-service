package io.github.temporalrift.game.shared.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;

/**
 * Configures Spring Modulith to externalize the ZenWave-generated producers' {@link Message}
 * events to Spring Cloud Stream bindings, keyed by {@code gameId} to preserve per-game Kafka
 * partition ordering.
 *
 * <p>Not {@code @EnableSpringCloudStreamEventExternalization}: that annotation's built-in
 * configuration routes without a partition key ({@code RoutingTarget.withoutKey()}), which would
 * silently drop the gameId-based ordering guarantee this design relies on.
 */
@Configuration
class KafkaExternalizationConfig {

    private static final String SCS_DESTINATION_HEADER = "spring.cloud.stream.sendto.destination";

    @Bean
    EventExternalizationConfiguration eventExternalizationConfiguration() {
        return EventExternalizationConfiguration.externalizing()
                .select(KafkaExternalizationConfig::isExternalizable)
                .route(
                        Message.class,
                        message ->
                                RoutingTarget.forTarget(scsDestination(message)).andKey(gameId(message)))
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
