package io.github.temporalrift.game.action.infrastructure.adapter.out.kafka;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.game.action.domain.event.ActionEventPayload;
import io.github.temporalrift.game.action.domain.event.ActionRoundStarted;
import io.github.temporalrift.game.action.domain.event.ActionRoundTimerExpired;
import io.github.temporalrift.game.action.domain.event.ActivistDeclarationRecorded;
import io.github.temporalrift.game.action.domain.event.BandedProbabilityPublished;
import io.github.temporalrift.game.action.domain.event.CardPlayed;
import io.github.temporalrift.game.action.domain.event.ExposeBehaviorChanged;
import io.github.temporalrift.game.action.domain.event.ExposeSignatureRevealed;
import io.github.temporalrift.game.action.domain.event.ParadoxResolutionCardPlayed;
import io.github.temporalrift.game.action.domain.event.PlayerSkipped;
import io.github.temporalrift.game.action.domain.event.RoundSummaryPublished;
import io.github.temporalrift.game.action.domain.event.SpecialActionPlayed;
import io.github.temporalrift.game.action.domain.port.out.ActionEventPublisher;
import io.github.temporalrift.game.shared.ActionRoundClosed;
import io.github.temporalrift.game.shared.DomainEventEnvelope;
import io.github.temporalrift.game.shared.OutboundIntegrationEventPublisher;

/** Publishes action events through the single durable {@code gameEvents} AsyncAPI channel. */
@Component
class ActionEventPublisherAdapter implements ActionEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final ActionEventWireMapper mapper;
    private final OutboundIntegrationEventPublisher outboundEvents;

    @Autowired
    ActionEventPublisherAdapter(
            ApplicationEventPublisher applicationEventPublisher,
            ActionEventWireMapper mapper,
            ObjectMapper objectMapper) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.mapper = mapper;
        this.outboundEvents = new OutboundIntegrationEventPublisher(applicationEventPublisher, objectMapper);
    }

    ActionEventPublisherAdapter(
            ApplicationEventPublisher applicationEventPublisher,
            ActionEventWireMapper mapper,
            OutboundIntegrationEventPublisher outboundEvents) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.mapper = mapper;
        this.outboundEvents = outboundEvents;
    }

    @Override
    public void publish(DomainEventEnvelope<ActionEventPayload> event) {
        switch (event.payload()) {
            case ActivistDeclarationRecorded payload ->
                outboundEvents.publish("ActivistDeclarationRecorded", mapper.toWire(payload), event);
            case ActionRoundStarted payload ->
                outboundEvents.publish("ActionRoundStarted", mapper.toWire(payload), event);
            case CardPlayed payload -> outboundEvents.publish("CardPlayed", mapper.toWire(payload), event);
            case ExposeSignatureRevealed payload ->
                outboundEvents.publish("ExposeSignatureRevealed", mapper.toWire(payload), event);
            case ExposeBehaviorChanged payload ->
                outboundEvents.publish("ExposeBehaviorChanged", mapper.toWire(payload), event);
            case ParadoxResolutionCardPlayed payload ->
                outboundEvents.publish("ParadoxResolutionCardPlayed", mapper.toWire(payload), event);
            case SpecialActionPlayed payload ->
                outboundEvents.publish("SpecialActionPlayed", mapper.toWire(payload), event);
            case ActionRoundTimerExpired payload ->
                outboundEvents.publish("ActionRoundTimerExpired", mapper.toWire(payload), event);
            case PlayerSkipped payload -> outboundEvents.publish("PlayerSkipped", mapper.toWire(payload), event);
            case RoundSummaryPublished payload ->
                outboundEvents.publish("RoundSummaryPublished", mapper.toWire(payload), event);
            case BandedProbabilityPublished payload ->
                outboundEvents.publish("BandedProbabilityPublished", mapper.toWire(payload), event);
        }
    }

    @Override
    public void publishRoundClosed(DomainEventEnvelope<ActionRoundClosed> event) {
        outboundEvents.publish("ActionRoundClosed", mapper.toWire(event.payload()), event);
    }

    @Override
    public void publishInternally(Object payload) {
        applicationEventPublisher.publishEvent(payload);
    }
}
