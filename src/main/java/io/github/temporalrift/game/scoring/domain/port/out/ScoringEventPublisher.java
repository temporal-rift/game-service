package io.github.temporalrift.game.scoring.domain.port.out;

import io.github.temporalrift.events.envelope.EventEnvelope;

public interface ScoringEventPublisher {

    void publish(EventEnvelope envelope);
}
