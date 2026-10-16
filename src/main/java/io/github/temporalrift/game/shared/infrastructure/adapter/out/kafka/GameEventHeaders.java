package io.github.temporalrift.game.shared.infrastructure.adapter.out.kafka;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** The headers a {@code game.events} record carries: the allowed envelope metadata plus event type and game id. */
final class GameEventHeaders {

    private static final Set<String> ALLOWED =
            Set.of("eventType", "eventId", "aggregateId", "aggregateType", "gameId", "occurredAt", "version");

    private GameEventHeaders() {}

    static Map<String, String> of(OutboundIntegrationEvent event) {
        var headers = new LinkedHashMap<String, String>();
        event.headers().forEach((name, value) -> {
            if (ALLOWED.contains(name)) {
                headers.put(name, String.valueOf(value));
            }
        });
        headers.put("eventType", event.eventType());
        headers.put("gameId", event.key());
        return headers;
    }
}
