package io.github.temporalrift.game.shared.infrastructure.adapter.out.kafka;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

/**
 * Hands durably registered game events to the host sink instead of Kafka, as JDK-only records. It runs on the
 * committing thread rather than an async pool, so records leave in commit order as one partition would carry them.
 */
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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
