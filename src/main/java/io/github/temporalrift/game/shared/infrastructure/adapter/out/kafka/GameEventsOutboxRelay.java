package io.github.temporalrift.game.shared.infrastructure.adapter.out.kafka;

import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Relays durably registered game events to the single Spring Cloud Stream output binding. */
@Component
@Profile("!in-memory")
class GameEventsOutboxRelay {

    private static final String OUTPUT_BINDING = "game-events-out";

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
        GameEventHeaders.of(event).forEach(message::setHeader);

        if (!streamBridge.send(OUTPUT_BINDING, message.build())) {
            throw new IllegalStateException("Spring Cloud Stream rejected game event " + event.eventType());
        }
    }
}
