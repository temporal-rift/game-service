package io.github.temporalrift.game.scoring.infrastructure.adapter.out.kafka;

import java.util.HashMap;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.scoring.ScoresUpdated;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringEventPublisher;
import io.github.temporalrift.game.scoring.infrastructure.adapter.out.kafka.producer.DefaultServiceEventsProducer;
import io.github.temporalrift.game.shared.DomainEventEnvelope;

@Component
class ScoringEventPublisherAdapter implements ScoringEventPublisher {

    private final DefaultServiceEventsProducer producer;
    private final ScoringEventWireMapper mapper;

    ScoringEventPublisherAdapter(DefaultServiceEventsProducer producer, ScoringEventWireMapper mapper) {
        this.producer = producer;
        this.mapper = mapper;
    }

    @Override
    public void publish(DomainEventEnvelope event) {
        switch (event.payload()) {
            case ScoresUpdated e ->
                producer.publishScoresUpdated(
                        mapper.toWire(e),
                        headers(new DefaultServiceEventsProducer.ScoresUpdatedPayloadHeaders(), event));
            default ->
                throw new IllegalArgumentException(
                        "Unsupported scoring event payload: " + event.payload().getClass());
        }
    }

    private static <H extends HashMap<String, Object>> H headers(H headers, DomainEventEnvelope event) {
        headers.put("eventId", event.eventId().toString());
        headers.put("aggregateId", event.aggregateId().toString());
        headers.put("aggregateType", event.aggregateType());
        headers.put("gameId", event.gameId().toString());
        headers.put("occurredAt", event.occurredAt());
        headers.put("version", event.version());
        return headers;
    }
}
