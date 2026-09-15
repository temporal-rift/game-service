package io.github.temporalrift.game.shared;

import java.util.function.Consumer;

import org.springframework.context.ApplicationEventPublisher;

/**
 * Publishes a saga-triggering domain fact through both the Kafka outbox path and the in-process
 * {@code @ApplicationModuleListener} path in one call, so a publish site can never emit one without the other.
 *
 * <p>Deliberately not a Spring bean — like {@link OutboundIntegrationEventPublisher}, each caller constructs its
 * own instance. Spring Modulith's module-entry observability wraps every bean in a CGLIB proxy and cannot resolve
 * the owning module for a call routed through a shared cross-module component, throwing a {@link
 * NullPointerException} from its tracing interceptor; a plain, unproxied instance avoids that entirely.
 */
public class SagaHandoffPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public SagaHandoffPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /** Publishes {@code envelope} to Kafka via {@code kafkaPublish}, then its payload in-process. */
    public <T> void publish(Consumer<DomainEventEnvelope<T>> kafkaPublish, DomainEventEnvelope<T> envelope) {
        publish(kafkaPublish, envelope, envelope.payload());
    }

    /**
     * Same as {@link #publish(Consumer, DomainEventEnvelope)}, for when the in-process payload is a distinct record
     * from the Kafka wire payload representing the same logical fact.
     */
    public <T> void publish(
            Consumer<DomainEventEnvelope<T>> kafkaPublish, DomainEventEnvelope<T> envelope, Object internalPayload) {
        kafkaPublish.accept(envelope);
        applicationEventPublisher.publishEvent(internalPayload);
    }
}
