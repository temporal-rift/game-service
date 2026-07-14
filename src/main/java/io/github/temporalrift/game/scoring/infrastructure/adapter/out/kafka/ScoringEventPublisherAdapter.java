package io.github.temporalrift.game.scoring.infrastructure.adapter.out.kafka;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringEventPublisher;

@Component
class ScoringEventPublisherAdapter implements ScoringEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    ScoringEventPublisherAdapter(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(EventEnvelope envelope) {
        applicationEventPublisher.publishEvent(envelope);
    }
}
