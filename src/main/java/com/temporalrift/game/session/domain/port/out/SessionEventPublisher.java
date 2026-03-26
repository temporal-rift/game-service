package com.temporalrift.game.session.domain.port.out;

import com.temporalrift.events.envelope.EventEnvelope;

public interface SessionEventPublisher {

    void publish(EventEnvelope event);
}
