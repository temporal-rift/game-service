package io.github.temporalrift.game.action.domain.port.out;

import io.github.temporalrift.game.shared.DomainEventEnvelope;

/**
 * Publishes action-module events to both integration and in-process consumers.
 *
 * <p>The external and internal paths are kept separate because a {@link DomainEventEnvelope} is only
 * the Kafka/outbox contract. Internal saga listeners subscribe to the typed payloads directly.
 */
public interface ActionEventPublisher {

    /**
     * Publishes the Kafka/outbox representation of an action event.
     */
    void publish(DomainEventEnvelope envelope);

    /**
     * Publishes the typed payload for in-process listeners inside the Modulith.
     */
    void publishInternally(Object payload);
}
