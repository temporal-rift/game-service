package io.github.temporalrift.game.shared;

import java.util.Map;

/**
 * Adds the common event-envelope metadata to generated producer header maps.
 */
public final class DomainEventHeaders {

    private DomainEventHeaders() {}

    public static <H extends Map<String, Object>> H populate(H headers, DomainEventEnvelope<?> event) {
        return populate(headers, event, null);
    }

    public static <H extends Map<String, Object>> H populate(
            H headers, DomainEventEnvelope<?> event, String eventType) {
        if (eventType != null) {
            headers.put("eventType", eventType);
        }
        headers.put("eventId", event.eventId().toString());
        headers.put("aggregateId", event.aggregateId().toString());
        headers.put("aggregateType", event.aggregateType());
        headers.put("gameId", event.gameId().toString());
        headers.put("occurredAt", event.occurredAt().toString());
        headers.put("version", String.valueOf(event.version()));
        return headers;
    }
}
