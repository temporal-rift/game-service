package io.github.temporalrift.game.session.infrastructure.adapter.out.kafka;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import io.github.temporalrift.game.session.domain.event.EraEnded;
import io.github.temporalrift.game.session.domain.event.EraFailed;
import io.github.temporalrift.game.session.domain.event.EraStarted;
import io.github.temporalrift.game.session.domain.event.FactionRevealed;
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
import io.github.temporalrift.game.session.domain.event.PlayerJoinedLobby;
import io.github.temporalrift.game.session.domain.event.PlayerLeftLobby;
import io.github.temporalrift.game.session.domain.event.TimelineCollapsed;
import io.github.temporalrift.game.session.domain.event.TimelineStabilized;
import io.github.temporalrift.game.session.domain.event.WinConditionMet;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.model.EraEndedPayload;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.model.EraFailedPayload;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.model.EraStartedPayload;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.model.EventsDrawnFutureEvent;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.model.EventsDrawnOutcome;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.model.EventsDrawnPayload;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.model.FactionAssignedPayload;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.model.FactionRevealedPayload;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.model.FactionRevealedPlayerFactionResult;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.model.FactionsDrawnPayload;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.model.GameEndedAbnormallyPayload;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.model.GameEndedPayload;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.model.GameEndedPlayerScoreResult;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.model.GameStartCancelledPayload;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.model.GameStartFailedPayload;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.model.GameStartedPayload;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.model.HandDealtCardInstance;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.model.HandDealtPayload;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.model.HostTransferredPayload;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.model.LobbyClosedPayload;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.model.LobbyCreatedPayload;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.model.PlayerAbandonedPayload;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.model.PlayerDisconnectedPayload;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.model.PlayerJoinedLobbyPayload;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.model.PlayerLeftLobbyPayload;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.model.TimelineCollapsedPayload;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.model.TimelineCollapsedPlayerFactionResult;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.model.TimelineStabilizedPayload;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.model.TimelineStabilizedPlayerFactionResult;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.model.WinConditionMetPayload;
import io.github.temporalrift.game.shared.EventsDrawn;
import io.github.temporalrift.game.shared.FactionAssigned;
import io.github.temporalrift.game.shared.GameEnded;
import io.github.temporalrift.game.shared.HandDealt;

@Mapper(componentModel = "spring")
interface SessionEventWireMapper {

    @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "toOffsetDateTime")
    LobbyCreatedPayload toWire(LobbyCreated event);

    PlayerJoinedLobbyPayload toWire(PlayerJoinedLobby event);

    PlayerLeftLobbyPayload toWire(PlayerLeftLobby event);

    LobbyClosedPayload toWire(LobbyClosed event);

    HostTransferredPayload toWire(HostTransferred event);

    EraStartedPayload toWire(EraStarted event);

    EraEndedPayload toWire(EraEnded event);

    EraFailedPayload toWire(EraFailed event);

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

    @Named("toOffsetDateTime")
    default OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
