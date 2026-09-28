package io.github.temporalrift.game.session.application.saga;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.session.domain.ending.SpecialEndingPolicy;
import io.github.temporalrift.game.session.domain.event.TimelineCollapsed;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.lobby.LobbyNotFoundException;
import io.github.temporalrift.game.session.domain.port.out.FinalScoreQueryPort;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.shared.application.SagaHandoffPublisher;
import io.github.temporalrift.game.shared.domain.event.GameEnded;
import io.github.temporalrift.game.shared.domain.messaging.DomainEventEnvelope;

/**
 * Single builder/publisher for the collapse ending so the deferred era-end decision and the late
 * collapse path cannot diverge on winner semantics.
 */
@Component
public class TimelineCollapsePublisher {

    private final LobbyRepository lobbyRepository;
    private final FinalScoreQueryPort scoreQueryPort;
    private final SessionEventPublisher eventPublisher;
    private final SagaHandoffPublisher sagaHandoffPublisher;
    private final Clock clock;

    public TimelineCollapsePublisher(
            LobbyRepository lobbyRepository,
            FinalScoreQueryPort scoreQueryPort,
            SessionEventPublisher eventPublisher,
            ApplicationEventPublisher applicationEventPublisher,
            Clock clock) {
        this.lobbyRepository = lobbyRepository;
        this.scoreQueryPort = scoreQueryPort;
        this.eventPublisher = eventPublisher;
        this.sagaHandoffPublisher = new SagaHandoffPublisher(applicationEventPublisher);
        this.clock = clock;
    }

    /** Both callers run after the collapsing era's scoring committed, so the queried totals are final for it. */
    public void publishCollapse(Game game, int eraNumber) {
        var players = lobbyRepository
                .findById(game.lobbyId())
                .orElseThrow(() -> new LobbyNotFoundException(game.lobbyId()))
                .currentPlayers();
        var scores = scoreQueryPort.getScores(game.id()).stream()
                .collect(Collectors.toMap(GameEnded.PlayerScoreResult::playerId, GameEnded.PlayerScoreResult::score));
        var standings = players.stream()
                .map(player -> new SpecialEndingPolicy.Standing(
                        player.playerId(),
                        player.faction(),
                        scores.getOrDefault(player.playerId(), 0),
                        0,
                        player.abandoned()))
                .toList();
        var winnerIds = SpecialEndingPolicy.collapseWinners(standings);
        var winners = new ArrayList<TimelineCollapsed.PlayerFactionResult>();
        var losers = new ArrayList<TimelineCollapsed.PlayerFactionResult>();
        for (var standing : standings) {
            var result = new TimelineCollapsed.PlayerFactionResult(
                    standing.playerId(), standing.faction().name());
            if (winnerIds.contains(standing.playerId())) {
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
