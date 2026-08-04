package io.github.temporalrift.game.shared.infrastructure.adapter.out.kafka;

import java.util.Map;
import java.util.Set;

import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.shared.OutboundIntegrationEvent;
import io.github.temporalrift.game.shared.OutboundIntegrationEventPublisher;

/** Relays durably registered game events to the single Spring Cloud Stream output binding. */
@Component
class GameEventsOutboxRelay {

    private static final String OUTPUT_BINDING = "game-events-out";
    private static final Set<String> ALLOWED_HEADERS =
            Set.of("eventType", "eventId", "aggregateId", "aggregateType", "gameId", "occurredAt", "version");

    private final StreamBridge streamBridge;

    GameEventsOutboxRelay(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    /**
     * Spring Modulith persists this listener invocation with the originating transaction. Throwing on a rejected send
     * leaves the publication incomplete so its configured resubmitter can retry it.
     */
    @ApplicationModuleListener
    void relay(OutboundIntegrationEvent event) {
        if (!OutboundIntegrationEventPublisher.GAME_EVENTS_CHANNEL.equals(event.channel())) {
            throw new IllegalArgumentException("Unsupported outbound AsyncAPI channel: " + event.channel());
        }

        var message = MessageBuilder.withPayload(event.payload());
        copyAllowedHeaders(event.headers(), message);
        message.setHeader("eventType", event.eventType());
        message.setHeader("gameId", event.key());

        if (!streamBridge.send(OUTPUT_BINDING, message.build())) {
            throw new IllegalStateException("Spring Cloud Stream rejected game event " + event.eventType());
        }
    }

    private static void copyAllowedHeaders(Map<String, Object> headers, MessageBuilder<?> message) {
        headers.forEach((name, value) -> {
            if (ALLOWED_HEADERS.contains(name)) {
                message.setHeader(name, value);
            }
        });
    }
}
