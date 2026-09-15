package io.github.temporalrift.game.scoring.domain.port.out;

import io.github.temporalrift.game.shared.domain.DomainEventEnvelope;

public interface ScoringEventPublisher {

    void publish(DomainEventEnvelope<?> envelope);
}
