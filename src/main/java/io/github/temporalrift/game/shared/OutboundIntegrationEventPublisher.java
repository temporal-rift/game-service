package io.github.temporalrift.game.shared;

import java.util.LinkedHashMap;

import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

/** Publishes an outbound event for durable delivery by Spring Modulith. */
public class OutboundIntegrationEventPublisher {

    public static final String GAME_EVENTS_CHANNEL = "gameEvents";

    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;

    public OutboundIntegrationEventPublisher(
            ApplicationEventPublisher applicationEventPublisher, ObjectMapper objectMapper) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.objectMapper = objectMapper;
    }

    /** Persists the event and relays it through the single channel-level outbound path. */
    public void publish(String eventType, Object payload, DomainEventEnvelope<?> envelope) {
        var headers = new LinkedHashMap<String, Object>();
        DomainEventHeaders.populate(headers, envelope, eventType);
        applicationEventPublisher.publishEvent(new OutboundIntegrationEvent(
                GAME_EVENTS_CHANNEL,
                eventType,
                envelope.gameId().toString(),
                objectMapper.valueToTree(payload),
                headers));
    }
}
