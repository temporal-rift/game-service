package com.temporalrift.game.session.infrastructure.adapter.out.kafka;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.temporalrift.events.envelope.EventEnvelope;
import com.temporalrift.game.session.domain.port.out.SessionEventPublisher;

/**
 * Driven adapter that fulfils the {@link SessionEventPublisher} port by delegating to Spring's
 * {@link ApplicationEventPublisher}.
 *
 * <p>Spring Modulith's JPA event publication intercepts every {@code publishEvent} call made inside
 * a transaction and writes a row to the {@code event_publication} table atomically. The outbox relay
 * ({@code spring-modulith-events-kafka}) then forwards the persisted event to Kafka after the
 * transaction commits. Events are <strong>never</strong> published to Kafka directly from here.
 */
@Component
class SessionEventPublisherAdapter implements SessionEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    SessionEventPublisherAdapter(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(EventEnvelope event) {
        applicationEventPublisher.publishEvent(event);
    }
}


