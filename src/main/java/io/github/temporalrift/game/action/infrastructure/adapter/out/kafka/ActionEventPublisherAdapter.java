package io.github.temporalrift.game.action.infrastructure.adapter.out.kafka;

import java.util.HashMap;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.action.domain.event.ActionRoundStarted;
import io.github.temporalrift.game.action.domain.event.ActionRoundTimerExpired;
import io.github.temporalrift.game.action.domain.event.BandedProbabilityPublished;
import io.github.temporalrift.game.action.domain.event.CardPlayed;
import io.github.temporalrift.game.action.domain.event.PlayerSkipped;
import io.github.temporalrift.game.action.domain.event.RoundSummaryPublished;
import io.github.temporalrift.game.action.domain.event.SpecialActionPlayed;
import io.github.temporalrift.game.action.domain.port.out.ActionEventPublisher;
import io.github.temporalrift.game.action.infrastructure.adapter.out.kafka.producer.DefaultServiceEventsProducer;
import io.github.temporalrift.game.shared.ActionRoundClosed;
import io.github.temporalrift.game.shared.DomainEventEnvelope;

/**
 * Driven adapter that fulfils the {@link ActionEventPublisher} port.
 *
 * <p>Each event's local payload is mapped to its generated wire type and published through the
 * ZenWave-generated {@link DefaultServiceEventsProducer}, which itself calls {@link
 * ApplicationEventPublisher#publishEvent} internally (transactionalOutbox=modulith).
 *
 * <p>Every generated {@code XxxPayloadHeaders} class is a distinct static nested type but they all
 * extend plain {@code HashMap<String,Object>}, so {@link #headers} populates the six common fields
 * generically instead of repeating the same six lines per case.
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
                        headers(new DefaultServiceEventsProducer.ActionRoundStartedPayloadHeaders(), event));
            case CardPlayed e ->
                producer.publishCardPlayed(
                        mapper.toWire(e), headers(new DefaultServiceEventsProducer.CardPlayedPayloadHeaders(), event));
            case SpecialActionPlayed e ->
                producer.publishSpecialActionPlayed(
                        mapper.toWire(e),
                        headers(new DefaultServiceEventsProducer.SpecialActionPlayedPayloadHeaders(), event));
            case ActionRoundTimerExpired e ->
                producer.publishActionRoundTimerExpired(
                        mapper.toWire(e),
                        headers(new DefaultServiceEventsProducer.ActionRoundTimerExpiredPayloadHeaders(), event));
            case PlayerSkipped e ->
                producer.publishPlayerSkipped(
                        mapper.toWire(e),
                        headers(new DefaultServiceEventsProducer.PlayerSkippedPayloadHeaders(), event));
            case ActionRoundClosed e ->
                producer.publishActionRoundClosed(
                        mapper.toWire(e),
                        headers(new DefaultServiceEventsProducer.ActionRoundClosedPayloadHeaders(), event));
            case RoundSummaryPublished e ->
                producer.publishRoundSummaryPublished(
                        mapper.toWire(e),
                        headers(new DefaultServiceEventsProducer.RoundSummaryPublishedPayloadHeaders(), event));
            case BandedProbabilityPublished e ->
                producer.publishBandedProbabilityPublished(
                        mapper.toWire(e),
                        headers(new DefaultServiceEventsProducer.BandedProbabilityPublishedPayloadHeaders(), event));
            default ->
                throw new IllegalArgumentException(
                        "Unsupported action event payload: " + event.payload().getClass());
        }
    }

    private static <H extends HashMap<String, Object>> H headers(H headers, DomainEventEnvelope event) {
        headers.put("eventId", event.eventId().toString());
        headers.put("aggregateId", event.aggregateId().toString());
        headers.put("aggregateType", event.aggregateType());
        headers.put("gameId", event.gameId().toString());
        headers.put("occurredAt", event.occurredAt());
        headers.put("version", event.version());
        return headers;
    }

    @Override
    public void publishInternally(Object payload) {
        applicationEventPublisher.publishEvent(payload);
    }
}
