package io.github.temporalrift.game.action.infrastructure.adapter.out.kafka;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.game.action.ActionRoundClosed;
import io.github.temporalrift.game.action.domain.event.ActionRoundStarted;
import io.github.temporalrift.game.action.domain.event.ActionRoundTimerExpired;
import io.github.temporalrift.game.action.domain.event.CardPlayed;
import io.github.temporalrift.game.action.domain.event.PlayerSkipped;
import io.github.temporalrift.game.action.domain.event.RoundSummaryPublished;
import io.github.temporalrift.game.action.domain.event.SpecialActionPlayed;
import io.github.temporalrift.game.action.domain.port.out.ActionEventPublisher;
import io.github.temporalrift.game.action.infrastructure.adapter.out.kafka.producer.DefaultServiceEventsProducer;
import io.github.temporalrift.game.shared.DomainEventEnvelope;

/**
 * Driven adapter that fulfils the {@link ActionEventPublisher} port.
 *
 * <p>Each event's local payload is mapped to its generated wire type and published through the
 * ZenWave-generated {@link DefaultServiceEventsProducer}, which itself calls {@link
 * ApplicationEventPublisher#publishEvent} internally (transactionalOutbox=modulith).
 *
 * <p>{@code BandedProbabilityPublished} isn't migrated yet - its apis spec entry (apis PR #4) hasn't
 * been published to Maven Central, so it still publishes a raw {@link EventEnvelope} directly, which
 * {@code KafkaExternalizationConfig} still recognizes for exactly this reason.
 */
@Component
class ActionEventPublisherAdapter implements ActionEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final DefaultServiceEventsProducer producer;
    private final ActionEventWireMapper mapper;

    ActionEventPublisherAdapter(
            ApplicationEventPublisher applicationEventPublisher,
            DefaultServiceEventsProducer producer,
            ActionEventWireMapper mapper) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.producer = producer;
        this.mapper = mapper;
    }

    @Override
    public void publish(DomainEventEnvelope event) {
        switch (event.payload()) {
            case ActionRoundStarted e ->
                producer.publishActionRoundStarted(
                        mapper.toWire(e),
                        new DefaultServiceEventsProducer.ActionRoundStartedPayloadHeaders()
                                .set("eventId", event.eventId().toString())
                                .set("aggregateId", event.aggregateId().toString())
                                .set("aggregateType", event.aggregateType())
                                .set("gameId", event.gameId().toString())
                                .set("occurredAt", event.occurredAt())
                                .set("version", event.version()));
            case CardPlayed e ->
                producer.publishCardPlayed(
                        mapper.toWire(e),
                        new DefaultServiceEventsProducer.CardPlayedPayloadHeaders()
                                .set("eventId", event.eventId().toString())
                                .set("aggregateId", event.aggregateId().toString())
                                .set("aggregateType", event.aggregateType())
                                .set("gameId", event.gameId().toString())
                                .set("occurredAt", event.occurredAt())
                                .set("version", event.version()));
            case SpecialActionPlayed e ->
                producer.publishSpecialActionPlayed(
                        mapper.toWire(e),
                        new DefaultServiceEventsProducer.SpecialActionPlayedPayloadHeaders()
                                .set("eventId", event.eventId().toString())
                                .set("aggregateId", event.aggregateId().toString())
                                .set("aggregateType", event.aggregateType())
                                .set("gameId", event.gameId().toString())
                                .set("occurredAt", event.occurredAt())
                                .set("version", event.version()));
            case ActionRoundTimerExpired e ->
                producer.publishActionRoundTimerExpired(
                        mapper.toWire(e),
                        new DefaultServiceEventsProducer.ActionRoundTimerExpiredPayloadHeaders()
                                .set("eventId", event.eventId().toString())
                                .set("aggregateId", event.aggregateId().toString())
                                .set("aggregateType", event.aggregateType())
                                .set("gameId", event.gameId().toString())
                                .set("occurredAt", event.occurredAt())
                                .set("version", event.version()));
            case PlayerSkipped e ->
                producer.publishPlayerSkipped(
                        mapper.toWire(e),
                        new DefaultServiceEventsProducer.PlayerSkippedPayloadHeaders()
                                .set("eventId", event.eventId().toString())
                                .set("aggregateId", event.aggregateId().toString())
                                .set("aggregateType", event.aggregateType())
                                .set("gameId", event.gameId().toString())
                                .set("occurredAt", event.occurredAt())
                                .set("version", event.version()));
            case ActionRoundClosed e ->
                producer.publishActionRoundClosed(
                        mapper.toWire(e),
                        new DefaultServiceEventsProducer.ActionRoundClosedPayloadHeaders()
                                .set("eventId", event.eventId().toString())
                                .set("aggregateId", event.aggregateId().toString())
                                .set("aggregateType", event.aggregateType())
                                .set("gameId", event.gameId().toString())
                                .set("occurredAt", event.occurredAt())
                                .set("version", event.version()));
            case RoundSummaryPublished e ->
                producer.publishRoundSummaryPublished(
                        mapper.toWire(e),
                        new DefaultServiceEventsProducer.RoundSummaryPublishedPayloadHeaders()
                                .set("eventId", event.eventId().toString())
                                .set("aggregateId", event.aggregateId().toString())
                                .set("aggregateType", event.aggregateType())
                                .set("gameId", event.gameId().toString())
                                .set("occurredAt", event.occurredAt())
                                .set("version", event.version()));
            default -> applicationEventPublisher.publishEvent(toLegacyEnvelope(event));
        }
    }

    @Override
    public void publishInternally(Object payload) {
        applicationEventPublisher.publishEvent(payload);
    }

    private EventEnvelope toLegacyEnvelope(DomainEventEnvelope event) {
        return new EventEnvelope(
                event.eventId(),
                deriveEventType(event.payload()),
                event.aggregateId(),
                event.aggregateType(),
                event.gameId(),
                event.occurredAt(),
                event.version(),
                event.payload());
    }

    private static String deriveEventType(Object payload) {
        var pkg = payload.getClass().getPackageName();
        return pkg.substring(pkg.lastIndexOf('.') + 1) + "."
                + payload.getClass().getSimpleName();
    }
}
