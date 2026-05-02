package io.github.temporalrift.game.session.application.saga;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
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

    private static final int FINAL_ROUND = 3;
    private static final Set<Faction> STABILIZATION_WINNERS = Set.of(Faction.PROPHETS, Faction.WEAVERS);

    private final EraSagaRepository eraSagaRepository;
    private final GameRepository gameRepository;
    private final SessionEventPublisher eventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final GameRulesPort gameRules;

    EraSagaAdvancer(
            EraSagaRepository eraSagaRepository,
            GameRepository gameRepository,
            SessionEventPublisher eventPublisher,
            ApplicationEventPublisher applicationEventPublisher,
            GameRulesPort gameRules) {
        this.eraSagaRepository = eraSagaRepository;
        this.gameRepository = gameRepository;
        this.eventPublisher = eventPublisher;
        this.applicationEventPublisher = applicationEventPublisher;
        this.gameRules = gameRules;
    }

    @Transactional(propagation = REQUIRES_NEW)
    void handleRoundClosed(UUID gameId, ActionRoundClosed arc) {
        var expectedStatus = findExpectedStatus(arc.roundNumber());
        if (expectedStatus.isEmpty()) {
            return;
        }
        eraSagaRepository
                .findByGameIdWithLock(gameId)
                .filter(s -> s.status() == expectedStatus.get())
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
                    publishEvent(gameId, new EraFailed(gameId, state.eraNumber(), reason));
                    publishEvent(gameId, new GameEndedAbnormally(gameId, "resolution-failed"));
                });
    }

    private static Optional<EraSagaStatus> findExpectedStatus(int roundNumber) {
        return switch (roundNumber) {
            case 1 -> Optional.of(EraSagaStatus.WAITING_ROUND_1);
            case 2 -> Optional.of(EraSagaStatus.WAITING_ROUND_2);
            case 3 -> Optional.of(EraSagaStatus.WAITING_ROUND_3);
            default -> Optional.empty();
        };
    }

    private void advanceRound(EraSagaState state, ActionRoundClosed arc) {
        if (arc.roundNumber() == FINAL_ROUND) {
            eraSagaRepository.save(state.withStatus(EraSagaStatus.WAITING_SCORES));
            publishEvent(state.gameId(), new ResolutionStarted(state.gameId(), state.eraNumber()));
        } else {
            var nextStatus = arc.roundNumber() == 1 ? EraSagaStatus.WAITING_ROUND_2 : EraSagaStatus.WAITING_ROUND_3;
            eraSagaRepository.save(state.withStatus(nextStatus));
        }
    }

    private void processScoresUpdated(UUID gameId, EraSagaState state, ScoresUpdated su) {
        findWinner(su)
                .ifPresentOrElse(
                        winner -> {
                            eraSagaRepository.save(state.withStatus(EraSagaStatus.COMPLETED));
                            var winConditionMet = new WinConditionMet(
                                    gameId,
                                    winner.playerId(),
                                    winner.faction().name(),
                                    winner.newTotal(),
                                    "SCORE_THRESHOLD");
                            publishEvent(gameId, winConditionMet);
                            applicationEventPublisher.publishEvent(winConditionMet);
                        },
                        () -> {
                            var game = gameRepository
                                    .findById(gameId)
                                    .orElseThrow(() -> new GameNotFoundException(gameId));
                            game.endEra(gameRules.maxEras());
                            gameRepository.save(game);
                            eraSagaRepository.save(state.withStatus(EraSagaStatus.COMPLETED));

                            if (game.status() == GameStatus.ENDED_BY_STABILIZATION) {
                                var stabilized = buildTimelineStabilized(gameId, su);
                                publishEvent(gameId, stabilized);
                                applicationEventPublisher.publishEvent(stabilized);
                            } else {
                                var nextEra = state.eraNumber() + 1;
                                publishEvent(
                                        gameId,
                                        new EraEnded(
                                                gameId, state.eraNumber(), game.cascadedParadoxCounter(), nextEra));
                                var eraStarted = new EraStarted(gameId, nextEra, List.of(), state.playerIds());
                                publishEvent(gameId, eraStarted);
                                applicationEventPublisher.publishEvent(eraStarted);
                            }
                        });
    }

    private Optional<ScoresUpdated.ScoreUpdate> findWinner(ScoresUpdated su) {
        return su.updates().stream()
                .filter(u -> u.newTotal() >= gameRules.winScoreThreshold())
                .max(Comparator.comparingInt(ScoresUpdated.ScoreUpdate::newTotal));
    }

    private void publishEvent(UUID gameId, Object payload) {
        eventPublisher.publish(EventEnvelope.create(gameId, Game.AGGREGATE_TYPE, gameId, 1, payload));
    }

    private TimelineStabilized buildTimelineStabilized(UUID gameId, ScoresUpdated su) {
        var winners = new ArrayList<TimelineStabilized.PlayerFactionResult>();
        var losers = new ArrayList<TimelineStabilized.PlayerFactionResult>();
        for (var update : su.updates()) {
            var result = new TimelineStabilized.PlayerFactionResult(
                    update.playerId(), update.faction().name(), null);
            if (STABILIZATION_WINNERS.contains(update.faction())) {
                winners.add(result);
            } else {
                losers.add(result);
            }
        }
        return new TimelineStabilized(gameId, winners, losers);
    }
}
