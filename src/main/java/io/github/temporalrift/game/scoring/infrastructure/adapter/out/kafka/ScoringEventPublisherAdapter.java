package io.github.temporalrift.game.scoring.infrastructure.adapter.out.kafka;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.scoring.domain.port.out.ScoringEventPublisher;
import io.github.temporalrift.game.shared.DomainEventEnvelope;
import io.github.temporalrift.game.shared.OutboundIntegrationEventPublisher;
import io.github.temporalrift.game.shared.ScoresUpdated;

/** Publishes scoring events through the single durable {@code gameEvents} AsyncAPI channel. */
@Component
class ScoringEventPublisherAdapter implements ScoringEventPublisher {

    private final ScoringEventWireMapper mapper;
    private final OutboundIntegrationEventPublisher outboundEvents;

    ScoringEventPublisherAdapter(ScoringEventWireMapper mapper, OutboundIntegrationEventPublisher outboundEvents) {
        this.mapper = mapper;
        this.outboundEvents = outboundEvents;
    }

    @Override
    public void publish(DomainEventEnvelope<?> event) {
        switch (event.payload()) {
            case ScoresUpdated payload -> outboundEvents.publish("ScoresUpdated", mapper.toWire(payload), event);
            default ->
                throw new IllegalArgumentException(
                        "Unsupported scoring event payload: " + event.payload().getClass());
        }
    }
}
