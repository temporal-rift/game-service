package io.github.temporalrift.game.session.infrastructure.adapter.out.kafka;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.game.session.domain.event.EraEnded;
import io.github.temporalrift.game.session.domain.event.EraFailed;
import io.github.temporalrift.game.session.domain.event.EraStarted;
import io.github.temporalrift.game.session.domain.event.FactionsDrawn;
import io.github.temporalrift.game.session.domain.event.GameEndedAbnormally;
import io.github.temporalrift.game.session.domain.event.GameStartCancelled;
import io.github.temporalrift.game.session.domain.event.GameStartFailed;
import io.github.temporalrift.game.session.domain.event.GameStarted;
import io.github.temporalrift.game.session.domain.event.HostTransferred;
import io.github.temporalrift.game.session.domain.event.LobbyClosed;
import io.github.temporalrift.game.session.domain.event.LobbyCreated;
import io.github.temporalrift.game.session.domain.event.PlayerAbandoned;
import io.github.temporalrift.game.session.domain.event.PlayerDisconnected;
import io.github.temporalrift.game.session.domain.event.PlayerLeftLobby;
import io.github.temporalrift.game.session.domain.event.ResolutionStarted;
import io.github.temporalrift.game.session.domain.event.TimelineCollapsed;
import io.github.temporalrift.game.session.domain.event.TimelineStabilized;
import io.github.temporalrift.game.session.domain.event.WinConditionMet;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.shared.DomainEventEnvelope;
import io.github.temporalrift.game.shared.EventsDrawn;
import io.github.temporalrift.game.shared.FactionAssigned;
import io.github.temporalrift.game.shared.FactionRevealed;
import io.github.temporalrift.game.shared.GameEnded;
import io.github.temporalrift.game.shared.HandDealt;
import io.github.temporalrift.game.shared.OutboundIntegrationEventPublisher;
import io.github.temporalrift.game.shared.PlayerJoinedLobby;

/** Publishes session events through the single durable {@code gameEvents} AsyncAPI channel. */
@Component
class SessionEventPublisherAdapter implements SessionEventPublisher {

    private final SessionEventWireMapper mapper;
    private final OutboundIntegrationEventPublisher outboundEvents;

    @Autowired
    SessionEventPublisherAdapter(
            SessionEventWireMapper mapper,
            ApplicationEventPublisher applicationEventPublisher,
            ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.outboundEvents = new OutboundIntegrationEventPublisher(applicationEventPublisher, objectMapper);
    }

    SessionEventPublisherAdapter(SessionEventWireMapper mapper, OutboundIntegrationEventPublisher outboundEvents) {
        this.mapper = mapper;
        this.outboundEvents = outboundEvents;
    }

    @Override
    public void publish(DomainEventEnvelope<?> event) {
        switch (event.payload()) {
            case LobbyCreated payload -> outboundEvents.publish("LobbyCreated", mapper.toWire(payload), event);
            case PlayerJoinedLobby payload ->
                outboundEvents.publish("PlayerJoinedLobby", mapper.toWire(payload), event);
            case PlayerLeftLobby payload -> outboundEvents.publish("PlayerLeftLobby", mapper.toWire(payload), event);
            case LobbyClosed payload -> outboundEvents.publish("LobbyClosed", mapper.toWire(payload), event);
            case HostTransferred payload -> outboundEvents.publish("HostTransferred", mapper.toWire(payload), event);
            case EraStarted payload -> outboundEvents.publish("EraStarted", mapper.toWire(payload), event);
            case EraEnded payload -> outboundEvents.publish("EraEnded", mapper.toWire(payload), event);
            case EraFailed payload -> outboundEvents.publish("EraFailed", mapper.toWire(payload), event);
            case FactionAssigned payload -> outboundEvents.publish("FactionAssigned", mapper.toWire(payload), event);
            case FactionsDrawn payload -> outboundEvents.publish("FactionsDrawn", mapper.toWire(payload), event);
            case GameStartCancelled payload ->
                outboundEvents.publish("GameStartCancelled", mapper.toWire(payload), event);
            case GameStartFailed payload -> outboundEvents.publish("GameStartFailed", mapper.toWire(payload), event);
            case GameStarted payload -> outboundEvents.publish("GameStarted", mapper.toWire(payload), event);
            case PlayerAbandoned payload -> outboundEvents.publish("PlayerAbandoned", mapper.toWire(payload), event);
            case PlayerDisconnected payload ->
                outboundEvents.publish("PlayerDisconnected", mapper.toWire(payload), event);
            case WinConditionMet payload -> outboundEvents.publish("WinConditionMet", mapper.toWire(payload), event);
            case GameEndedAbnormally payload ->
                outboundEvents.publish("GameEndedAbnormally", mapper.toWire(payload), event);
            case GameEnded payload -> outboundEvents.publish("GameEnded", mapper.toWire(payload), event);
            case TimelineCollapsed payload ->
                outboundEvents.publish("TimelineCollapsed", mapper.toWire(payload), event);
            case TimelineStabilized payload ->
                outboundEvents.publish("TimelineStabilized", mapper.toWire(payload), event);
            case FactionRevealed payload -> outboundEvents.publish("FactionRevealed", mapper.toWire(payload), event);
            case EventsDrawn payload -> outboundEvents.publish("EventsDrawn", mapper.toWire(payload), event);
            case HandDealt payload -> outboundEvents.publish("HandDealt", mapper.toWire(payload), event);
            case ResolutionStarted payload ->
                outboundEvents.publish("ResolutionStarted", mapper.toWire(payload), event);
            default ->
                throw new IllegalArgumentException(
                        "Unsupported session event payload: " + event.payload().getClass());
        }
    }
}
