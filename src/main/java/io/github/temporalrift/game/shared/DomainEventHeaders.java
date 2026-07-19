package io.github.temporalrift.game.shared;

import java.util.Map;

/**
 * Adds the common event-envelope metadata to generated producer header maps.
 */
public final class DomainEventHeaders {

    private DomainEventHeaders() {}

    public static <H extends Map<String, Object>> H populate(H headers, DomainEventEnvelope<?> event) {
        headers.put("eventId", event.eventId().toString());
        headers.put("aggregateId", event.aggregateId().toString());
        headers.put("aggregateType", event.aggregateType());
        headers.put("gameId", event.gameId().toString());
        headers.put("occurredAt", event.occurredAt());
        headers.put("version", event.version());
        return headers;
    }
}
