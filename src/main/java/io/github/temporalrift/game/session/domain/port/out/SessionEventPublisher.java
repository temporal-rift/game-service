package io.github.temporalrift.game.session.domain.port.out;

import io.github.temporalrift.game.shared.DomainEventEnvelope;

public interface SessionEventPublisher {

    void publish(DomainEventEnvelope event);
}
