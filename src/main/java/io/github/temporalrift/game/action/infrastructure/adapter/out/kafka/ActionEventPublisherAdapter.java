package io.github.temporalrift.game.action.infrastructure.adapter.out.kafka;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.action.ActionRoundClosed;
import io.github.temporalrift.game.action.domain.event.ActionRoundStarted;
import io.github.temporalrift.game.action.domain.event.ActionRoundTimerExpired;
import io.github.temporalrift.game.action.domain.event.BandedProbabilityPublished;
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
            case BandedProbabilityPublished e ->
                producer.publishBandedProbabilityPublished(
                        mapper.toWire(e),
                        new DefaultServiceEventsProducer.BandedProbabilityPublishedPayloadHeaders()
                                .set("eventId", event.eventId().toString())
                                .set("aggregateId", event.aggregateId().toString())
                                .set("aggregateType", event.aggregateType())
                                .set("gameId", event.gameId().toString())
                                .set("occurredAt", event.occurredAt())
                                .set("version", event.version()));
            default ->
                throw new IllegalArgumentException(
                        "Unsupported action event payload: " + event.payload().getClass());
        }
    }

    @Override
    public void publishInternally(Object payload) {
        applicationEventPublisher.publishEvent(payload);
    }
}
