package io.github.temporalrift.game.scoring.infrastructure.adapter.out.kafka;

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
                        new DefaultServiceEventsProducer.ScoresUpdatedPayloadHeaders()
                                .set("eventId", event.eventId().toString())
                                .set("aggregateId", event.aggregateId().toString())
                                .set("aggregateType", event.aggregateType())
                                .set("gameId", event.gameId().toString())
                                .set("occurredAt", event.occurredAt())
                                .set("version", event.version()));
            default ->
                throw new IllegalArgumentException(
                        "Unsupported scoring event payload: " + event.payload().getClass());
        }
    }
}
