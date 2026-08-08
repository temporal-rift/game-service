package io.github.temporalrift.game.session.infrastructure.adapter.out.kafka;

import java.util.UUID;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.EraEndedPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.EraFailedPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.EraStartedPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.EventsDrawnFutureEvent;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.EventsDrawnOutcome;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.EventsDrawnPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.FactionAssignedPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.FactionRevealedPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.FactionRevealedPlayerFactionResult;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.FactionsDrawnPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.GameEndedAbnormallyPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.GameEndedPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.GameEndedPlayerScoreResult;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.GameStartCancelledPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.GameStartFailedPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.GameStartedPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.HandDealtCardInstance;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.HandDealtPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.HostTransferredPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.LobbyClosedPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.LobbyCreatedPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.PlayerAbandonedPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.PlayerDisconnectedPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.PlayerJoinedLobbyPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.PlayerLeftLobbyPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.ResolutionStartedPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.TimelineCollapsedPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.TimelineCollapsedPlayerFactionResult;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.TimelineStabilizedPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.TimelineStabilizedPlayerFactionResult;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.WinConditionMetPayload;
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
import io.github.temporalrift.game.session.domain.game.PendingCarryOverEvent;
import io.github.temporalrift.game.shared.EventsDrawn;
import io.github.temporalrift.game.shared.FactionAssigned;
import io.github.temporalrift.game.shared.FactionRevealed;
import io.github.temporalrift.game.shared.GameEnded;
import io.github.temporalrift.game.shared.HandDealt;
import io.github.temporalrift.game.shared.PlayerJoinedLobby;

@Mapper(componentModel = "spring")
interface SessionEventWireMapper {

    LobbyCreatedPayload toWire(LobbyCreated event);

    PlayerJoinedLobbyPayload toWire(PlayerJoinedLobby event);

    PlayerLeftLobbyPayload toWire(PlayerLeftLobby event);

    LobbyClosedPayload toWire(LobbyClosed event);

    HostTransferredPayload toWire(HostTransferred event);

    @Mapping(target = "carryOverEventIds", source = "carryOverEvents")
    EraStartedPayload toWire(EraStarted event);

    default UUID toWire(PendingCarryOverEvent event) {
        return event.eventId();
    }

    EraEndedPayload toWire(EraEnded event);

    EraFailedPayload toWire(EraFailed event);

    ResolutionStartedPayload toWire(ResolutionStarted event);

    FactionAssignedPayload toWire(FactionAssigned event);

    FactionsDrawnPayload toWire(FactionsDrawn event);

    GameStartCancelledPayload toWire(GameStartCancelled event);

    GameStartFailedPayload toWire(GameStartFailed event);

    GameStartedPayload toWire(GameStarted event);

    PlayerAbandonedPayload toWire(PlayerAbandoned event);

    PlayerDisconnectedPayload toWire(PlayerDisconnected event);

    WinConditionMetPayload toWire(WinConditionMet event);

    GameEndedAbnormallyPayload toWire(GameEndedAbnormally event);

    GameEndedPayload toWire(GameEnded event);

    GameEndedPlayerScoreResult toWire(GameEnded.PlayerScoreResult result);

    TimelineCollapsedPayload toWire(TimelineCollapsed event);

    TimelineCollapsedPlayerFactionResult toWire(TimelineCollapsed.PlayerFactionResult result);

    TimelineStabilizedPayload toWire(TimelineStabilized event);

    TimelineStabilizedPlayerFactionResult toWire(TimelineStabilized.PlayerFactionResult result);

    FactionRevealedPayload toWire(FactionRevealed event);

    FactionRevealedPlayerFactionResult toWire(FactionRevealed.PlayerFactionResult result);

    EventsDrawnPayload toWire(EventsDrawn event);

    EventsDrawnFutureEvent toWire(EventsDrawn.FutureEvent event);

    EventsDrawnOutcome toWire(EventsDrawn.Outcome outcome);

    HandDealtPayload toWire(HandDealt event);

    HandDealtCardInstance toWire(HandDealt.CardInstance cardInstance);
}
