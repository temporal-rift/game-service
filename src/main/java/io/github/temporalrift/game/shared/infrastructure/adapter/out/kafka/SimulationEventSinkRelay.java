package io.github.temporalrift.game.shared.infrastructure.adapter.out.kafka;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Hands durably registered game events to the host sink instead of Kafka, as JDK-only records. */
@Component
@Profile("in-memory")
class SimulationEventSinkRelay {

    private static final String TOPIC = "game.events";

    private final Consumer<Map<String, Object>> sink;
    private final ObjectMapper objectMapper;

    SimulationEventSinkRelay(
            @Qualifier("simulationEventSink") Consumer<Map<String, Object>> sink, ObjectMapper objectMapper) {
        this.sink = sink;
        this.objectMapper = objectMapper;
    }

    @ApplicationModuleListener
    void relay(OutboundIntegrationEvent event) {
        if (!OutboundIntegrationEventPublisher.GAME_EVENTS_CHANNEL.equals(event.channel())) {
            throw new IllegalArgumentException("Unsupported outbound AsyncAPI channel: " + event.channel());
        }
        var message = new LinkedHashMap<String, Object>();
        message.put("topic", TOPIC);
        message.put("key", event.key());
        message.put("headers", GameEventHeaders.of(event));
        message.put("payload", objectMapper.writeValueAsBytes(event.payload()));
        sink.accept(message);
    }
}
