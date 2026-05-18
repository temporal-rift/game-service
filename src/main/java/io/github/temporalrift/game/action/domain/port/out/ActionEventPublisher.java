package io.github.temporalrift.game.action.domain.port.out;

import io.github.temporalrift.events.envelope.EventEnvelope;

public interface ActionEventPublisher {

    void publish(EventEnvelope envelope);

    void publishInternally(Object payload);
}
