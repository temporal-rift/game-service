package io.github.temporalrift.game.session.application.saga;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.session.domain.event.TimelineCollapsed;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.lobby.LobbyNotFoundException;
import io.github.temporalrift.game.session.domain.lobby.LobbyPlayer;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionActivistDeclarationRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.shared.application.SagaHandoffPublisher;
import io.github.temporalrift.game.shared.domain.messaging.DomainEventEnvelope;
import io.github.temporalrift.game.shared.domain.model.Faction;

/**
 * Single builder/publisher for the collapse ending so the deferred era-end decision and the late
 * collapse path cannot diverge on winner semantics.
 */
@Component
public class TimelineCollapsePublisher {

    private final LobbyRepository lobbyRepository;
    private final SessionActivistDeclarationRepository declarationRepository;
    private final SessionEventPublisher eventPublisher;
    private final SagaHandoffPublisher sagaHandoffPublisher;
    private final Clock clock;

    public TimelineCollapsePublisher(
            LobbyRepository lobbyRepository,
            SessionActivistDeclarationRepository declarationRepository,
            SessionEventPublisher eventPublisher,
            ApplicationEventPublisher applicationEventPublisher,
            Clock clock) {
        this.lobbyRepository = lobbyRepository;
        this.declarationRepository = declarationRepository;
        this.eventPublisher = eventPublisher;
        this.sagaHandoffPublisher = new SagaHandoffPublisher(applicationEventPublisher);
        this.clock = clock;
    }

    public void publishCollapse(Game game, int eraNumber, UUID collapsingEventId) {
        var players = lobbyRepository
                .findById(game.lobbyId())
                .orElseThrow(() -> new LobbyNotFoundException(game.lobbyId()))
                .currentPlayers();
        var targeting = declarationRepository.findPlayerIdsTargeting(game.id(), eraNumber, collapsingEventId);
        // Collapse is an Activist special ending: only Activists whose current-era declaration
        // targets the collapsing event win. The declared outcome is irrelevant because a cascaded
        // event has no resolved winner.
        var winnerIds = players.stream()
                .filter(player -> player.faction() == Faction.ACTIVISTS)
                .map(LobbyPlayer::playerId)
                .filter(targeting::contains)
                .toList();
        var winners = new ArrayList<TimelineCollapsed.PlayerFactionResult>();
        var losers = new ArrayList<TimelineCollapsed.PlayerFactionResult>();
        for (var player : players) {
            var faction = player.faction();
            var result = new TimelineCollapsed.PlayerFactionResult(
                    player.playerId(), faction == null ? null : faction.name());
            if (winnerIds.contains(player.playerId())) {
                winners.add(result);
            } else {
                losers.add(result);
            }
        }
        var collapsed = new TimelineCollapsed(game.id(), eraNumber, List.copyOf(winners), List.copyOf(losers));
        sagaHandoffPublisher.publish(
                eventPublisher::publish,
                DomainEventEnvelope.create(
                        game.id(),
                        Game.AGGREGATE_TYPE,
                        game.id(),
                        DomainEventEnvelope.SCHEMA_VERSION_V1,
                        collapsed,
                        clock));
    }
}
