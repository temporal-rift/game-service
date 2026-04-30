package io.github.temporalrift.game.session.application.saga;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.events.action.ActionRoundClosed;
import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.events.scoring.ScoresUpdated;
import io.github.temporalrift.events.session.EraEnded;
import io.github.temporalrift.events.session.EraFailed;
import io.github.temporalrift.events.session.EraStarted;
import io.github.temporalrift.events.session.GameEndedAbnormally;
import io.github.temporalrift.events.session.TimelineStabilized;
import io.github.temporalrift.events.session.WinConditionMet;
import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.events.timeline.ResolutionStarted;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.game.GameNotFoundException;
import io.github.temporalrift.game.session.domain.game.GameStatus;
import io.github.temporalrift.game.session.domain.port.out.EraSagaRepository;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.GameRulesPort;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.session.domain.saga.EraSagaState;
import io.github.temporalrift.game.session.domain.saga.EraSagaStatus;

@Component
class EraSagaAdvancer {

    private final EraSagaRepository eraSagaRepository;
    private final GameRepository gameRepository;
    private final SessionEventPublisher eventPublisher;
    private final GameRulesPort gameRules;

    EraSagaAdvancer(
            EraSagaRepository eraSagaRepository,
            GameRepository gameRepository,
            SessionEventPublisher eventPublisher,
            GameRulesPort gameRules) {
        this.eraSagaRepository = eraSagaRepository;
        this.gameRepository = gameRepository;
        this.eventPublisher = eventPublisher;
        this.gameRules = gameRules;
    }

    @EventListener
    void onEvent(EventEnvelope envelope) {
        switch (envelope.payload()) {
            case ActionRoundClosed arc -> handleRoundClosed(envelope.gameId(), arc);
            case ScoresUpdated su -> handleScoresUpdated(envelope.gameId(), su);
            default -> {}
        }
    }

    @Transactional(propagation = REQUIRES_NEW)
    void handleRoundClosed(UUID gameId, ActionRoundClosed arc) {
        var expectedStatus =
                switch (arc.roundNumber()) {
                    case 1 -> EraSagaStatus.WAITING_ROUND_1;
                    case 2 -> EraSagaStatus.WAITING_ROUND_2;
                    case 3 -> EraSagaStatus.WAITING_ROUND_3;
                    default -> null;
                };
        if (expectedStatus == null) {
            return;
        }
        eraSagaRepository
                .findByGameIdWithLock(gameId)
                .filter(s -> s.status() == expectedStatus)
                .ifPresent(state -> advanceRound(state, arc));
    }

    @Transactional(propagation = REQUIRES_NEW)
    void handleScoresUpdated(UUID gameId, ScoresUpdated su) {
        eraSagaRepository
                .findByGameIdWithLock(gameId)
                .filter(s -> s.status() == EraSagaStatus.WAITING_SCORES)
                .ifPresent(state -> processScoresUpdated(gameId, state, su));
    }

    /**
     * Called when ResolutionSaga (timeline-service) reports failure.
     * Wired to an incoming Kafka consumer once ResolutionSaga is implemented.
     */
    @Transactional(propagation = REQUIRES_NEW)
    void handleResolutionFailed(UUID gameId, String reason) {
        eraSagaRepository
                .findByGameIdWithLock(gameId)
                .filter(s -> s.status() == EraSagaStatus.WAITING_SCORES)
                .ifPresent(state -> {
                    eraSagaRepository.save(state.withStatus(EraSagaStatus.FAILED));
                    eventPublisher.publish(EventEnvelope.create(
                            gameId, Game.AGGREGATE_TYPE, gameId, 1, new EraFailed(gameId, state.eraNumber(), reason)));
                    eventPublisher.publish(EventEnvelope.create(
                            gameId,
                            Game.AGGREGATE_TYPE,
                            gameId,
                            1,
                            new GameEndedAbnormally(gameId, "resolution-failed")));
                });
    }

    private void advanceRound(EraSagaState state, ActionRoundClosed arc) {
        if (arc.roundNumber() == 3) {
            eraSagaRepository.save(state.withStatus(EraSagaStatus.WAITING_SCORES));
            eventPublisher.publish(EventEnvelope.create(
                    state.gameId(),
                    Game.AGGREGATE_TYPE,
                    state.gameId(),
                    1,
                    new ResolutionStarted(state.gameId(), state.eraNumber())));
        } else {
            var nextStatus = arc.roundNumber() == 1 ? EraSagaStatus.WAITING_ROUND_2 : EraSagaStatus.WAITING_ROUND_3;
            eraSagaRepository.save(state.withStatus(nextStatus));
        }
    }

    private void processScoresUpdated(UUID gameId, EraSagaState state, ScoresUpdated su) {
        var winner = su.updates().stream()
                .filter(u -> u.newTotal() >= gameRules.winScoreThreshold())
                .max(Comparator.comparingInt(ScoresUpdated.ScoreUpdate::newTotal))
                .orElse(null);

        if (winner != null) {
            eraSagaRepository.save(state.withStatus(EraSagaStatus.COMPLETED));
            eventPublisher.publish(EventEnvelope.create(
                    gameId,
                    Game.AGGREGATE_TYPE,
                    gameId,
                    1,
                    new WinConditionMet(
                            gameId, winner.playerId(), winner.faction().name(), winner.newTotal(), "SCORE_THRESHOLD")));
            return;
        }

        var game = gameRepository.findById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
        game.endEra(gameRules.maxEras());
        gameRepository.save(game);
        eraSagaRepository.save(state.withStatus(EraSagaStatus.COMPLETED));

        if (game.status() == GameStatus.ENDED_BY_STABILIZATION) {
            eventPublisher.publish(
                    EventEnvelope.create(gameId, Game.AGGREGATE_TYPE, gameId, 1, buildTimelineStabilized(gameId, su)));
        } else {
            var nextEra = state.eraNumber() + 1;
            eventPublisher.publish(EventEnvelope.create(
                    gameId,
                    Game.AGGREGATE_TYPE,
                    gameId,
                    1,
                    new EraEnded(gameId, state.eraNumber(), game.cascadedParadoxCounter(), nextEra)));
            eventPublisher.publish(EventEnvelope.create(
                    gameId,
                    Game.AGGREGATE_TYPE,
                    gameId,
                    1,
                    new EraStarted(gameId, nextEra, List.of(), state.playerIds())));
        }
    }

    private TimelineStabilized buildTimelineStabilized(UUID gameId, ScoresUpdated su) {
        var winners = new ArrayList<TimelineStabilized.PlayerFactionResult>();
        var losers = new ArrayList<TimelineStabilized.PlayerFactionResult>();
        for (var update : su.updates()) {
            var result = new TimelineStabilized.PlayerFactionResult(
                    update.playerId(), update.faction().name(), null);
            if (isWinnerOnStabilization(update.faction())) {
                winners.add(result);
            } else {
                losers.add(result);
            }
        }
        return new TimelineStabilized(gameId, winners, losers);
    }

    private static boolean isWinnerOnStabilization(Faction faction) {
        return faction == Faction.PROPHETS || faction == Faction.WEAVERS;
    }
}
