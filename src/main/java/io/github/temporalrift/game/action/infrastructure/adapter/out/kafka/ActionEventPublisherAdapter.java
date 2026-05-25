package io.github.temporalrift.game.action.infrastructure.adapter.out.kafka;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.game.action.domain.port.out.ActionEventPublisher;

/**
 * Driven adapter for action-module event publication.
 *
 * <p>External events are published as {@link EventEnvelope} instances so Spring Modulith can persist
 * them into the outbox within the surrounding transaction. Internal listeners consume the raw payload
 * types through Spring's application event bus.
 */
@Component
class ActionEventPublisherAdapter implements ActionEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    ActionEventPublisherAdapter(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(EventEnvelope envelope) {
        applicationEventPublisher.publishEvent(envelope);
    }

    @Override
    public void publishInternally(Object payload) {
        applicationEventPublisher.publishEvent(payload);
    }
}
