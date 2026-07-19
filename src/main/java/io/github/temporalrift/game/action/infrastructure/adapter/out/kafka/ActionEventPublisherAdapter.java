package io.github.temporalrift.game.action.infrastructure.adapter.out.kafka;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.action.domain.event.ActionEventPayload;
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
import io.github.temporalrift.game.shared.DomainEventHeaders;

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
    public void publish(DomainEventEnvelope<ActionEventPayload> event) {
        switch (event.payload()) {
            case ActionRoundStarted e ->
                producer.publishActionRoundStarted(
                        mapper.toWire(e),
                        DomainEventHeaders.populate(
                                new DefaultServiceEventsProducer.ActionRoundStartedPayloadHeaders(), event));
            case CardPlayed e ->
                producer.publishCardPlayed(
                        mapper.toWire(e),
                        DomainEventHeaders.populate(
                                new DefaultServiceEventsProducer.CardPlayedPayloadHeaders(), event));
            case SpecialActionPlayed e ->
                producer.publishSpecialActionPlayed(
                        mapper.toWire(e),
                        DomainEventHeaders.populate(
                                new DefaultServiceEventsProducer.SpecialActionPlayedPayloadHeaders(), event));
            case ActionRoundTimerExpired e ->
                producer.publishActionRoundTimerExpired(
                        mapper.toWire(e),
                        DomainEventHeaders.populate(
                                new DefaultServiceEventsProducer.ActionRoundTimerExpiredPayloadHeaders(), event));
            case PlayerSkipped e ->
                producer.publishPlayerSkipped(
                        mapper.toWire(e),
                        DomainEventHeaders.populate(
                                new DefaultServiceEventsProducer.PlayerSkippedPayloadHeaders(), event));
            case RoundSummaryPublished e ->
                producer.publishRoundSummaryPublished(
                        mapper.toWire(e),
                        DomainEventHeaders.populate(
                                new DefaultServiceEventsProducer.RoundSummaryPublishedPayloadHeaders(), event));
            case BandedProbabilityPublished e ->
                producer.publishBandedProbabilityPublished(
                        mapper.toWire(e),
                        DomainEventHeaders.populate(
                                new DefaultServiceEventsProducer.BandedProbabilityPublishedPayloadHeaders(), event));
        }
    }

    @Override
    public void publishRoundClosed(DomainEventEnvelope<ActionRoundClosed> event) {
        producer.publishActionRoundClosed(
                mapper.toWire(event.payload()),
                DomainEventHeaders.populate(new DefaultServiceEventsProducer.ActionRoundClosedPayloadHeaders(), event));
    }

    @Override
    public void publishInternally(Object payload) {
        applicationEventPublisher.publishEvent(payload);
    }
}
