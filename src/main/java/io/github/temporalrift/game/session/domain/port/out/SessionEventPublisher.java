package io.github.temporalrift.game.session.domain.port.out;

import io.github.temporalrift.events.envelope.EventEnvelope;

public interface SessionEventPublisher {

    void publish(EventEnvelope event);
}
