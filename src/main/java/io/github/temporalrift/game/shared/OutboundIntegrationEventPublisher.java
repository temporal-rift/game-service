package io.github.temporalrift.game.shared;

import java.util.LinkedHashMap;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

/** Publishes an outbound event for durable delivery by Spring Modulith. */
public class OutboundIntegrationEventPublisher {

    public static final String GAME_EVENTS_CHANNEL = "gameEvents";

    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public OutboundIntegrationEventPublisher(
            ApplicationEventPublisher applicationEventPublisher, ObjectMapper objectMapper, Validator validator) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    /** Persists the event and relays it through the single channel-level outbound path. */
    public void publish(String eventType, Object payload, DomainEventEnvelope<?> envelope) {
        validatePayload(eventType, payload);
        var headers = new LinkedHashMap<String, Object>();
        DomainEventHeaders.populate(headers, envelope, eventType);
        applicationEventPublisher.publishEvent(new OutboundIntegrationEvent(
                GAME_EVENTS_CHANNEL,
                eventType,
                envelope.gameId().toString(),
                objectMapper.valueToTree(payload),
                headers));
    }

    private void validatePayload(String eventType, Object payload) {
        var violations = validator.validate(payload);
        if (!violations.isEmpty()) {
            var details = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; "));
            throw new ConstraintViolationException(
                    "Invalid game event payload '" + eventType + "': " + details, violations);
        }
    }
}
