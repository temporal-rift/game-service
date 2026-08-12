package io.github.temporalrift.game.session.application.saga;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.session.domain.event.EraEnded;
import io.github.temporalrift.game.session.domain.event.EraFailed;
import io.github.temporalrift.game.session.domain.event.EraStarted;
import io.github.temporalrift.game.session.domain.event.GameEndedAbnormally;
import io.github.temporalrift.game.session.domain.event.ResolutionStarted;
import io.github.temporalrift.game.session.domain.event.TimelineStabilized;
import io.github.temporalrift.game.session.domain.event.WinConditionMet;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.game.GameNotFoundException;
import io.github.temporalrift.game.session.domain.game.GameStatus;
import io.github.temporalrift.game.session.domain.port.out.EraSagaRepository;
import io.github.temporalrift.game.session.domain.port.out.EraSagaScoresUpdatedInboxRepository;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.session.domain.port.out.SessionGameRulesPort;
import io.github.temporalrift.game.session.domain.saga.EraSagaState;
import io.github.temporalrift.game.session.domain.saga.EraSagaStatus;
import io.github.temporalrift.game.shared.ActionRoundClosed;
import io.github.temporalrift.game.shared.DomainEventEnvelope;
import io.github.temporalrift.game.shared.ScoresUpdated;
import io.github.temporalrift.game.shared.StartActionRoundRequested;

@Component
class EraSagaAdvancer {

    private static final int FINAL_ROUND = 3;
    private static final String RESOLUTION_FAILED_REASON = "resolution-failed";

    private final EraSagaRepository eraSagaRepository;
    private final EraSagaScoresUpdatedInboxRepository scoresUpdatedInbox;
    private final GameRepository gameRepository;
    private final SessionEventPublisher eventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final SessionGameRulesPort gameRules;
    private final Clock clock;

    EraSagaAdvancer(
            EraSagaRepository eraSagaRepository,
            EraSagaScoresUpdatedInboxRepository scoresUpdatedInbox,
            GameRepository gameRepository,
            SessionEventPublisher eventPublisher,
            ApplicationEventPublisher applicationEventPublisher,
            SessionGameRulesPort gameRules,
            Clock clock) {
        this.eraSagaRepository = eraSagaRepository;
        this.scoresUpdatedInbox = scoresUpdatedInbox;
        this.gameRepository = gameRepository;
        this.eventPublisher = eventPublisher;
        this.applicationEventPublisher = applicationEventPublisher;
        this.gameRules = gameRules;
        this.clock = clock;
    }

    private static Optional<EraSagaStatus> findExpectedStatus(int roundNumber) {
        return switch (roundNumber) {
            case 1 -> Optional.of(EraSagaStatus.WAITING_ROUND_1);
            case 2 -> Optional.of(EraSagaStatus.WAITING_ROUND_2);
            case 3 -> Optional.of(EraSagaStatus.WAITING_ROUND_3);
            default -> Optional.empty();
        };
    }

    @Transactional(propagation = REQUIRES_NEW)
    void handleRoundClosed(UUID gameId, ActionRoundClosed arc) {
        findExpectedStatus(arc.roundNumber())
                .ifPresent(expectedStatus -> eraSagaRepository
                        .findByGameIdWithLock(gameId)
                        .filter(s -> s.status() == expectedStatus)
                        .ifPresent(state -> advanceRound(state, arc)));
    }

    @Transactional(propagation = REQUIRES_NEW)
    void handleScoresUpdated(UUID gameId, ScoresUpdated su) {
        // Recorded durably before the status check below: ActionRoundClosed (round 3) sets
        // WAITING_SCORES and ScoresUpdated fires from an independent async chain (timeline-service
        // resolution -> scoring), so either can arrive first. If this one loses the race, the record
        // lets EraSagaScoresUpdatedSweep complete the transition later without a second delivery.
        scoresUpdatedInbox.record(su);
        eraSagaRepository
                .findByGameIdWithLock(gameId)
                .filter(s -> s.status() == EraSagaStatus.WAITING_SCORES && s.eraNumber() == su.eraNumber())
                .ifPresent(state -> processScoresUpdated(gameId, state, su));
    }

    @Transactional(propagation = REQUIRES_NEW)
    void handleResolutionFailed(UUID gameId, int eraNumber) {
        eraSagaRepository
                .findByGameIdWithLock(gameId)
                .filter(s -> s.status() == EraSagaStatus.WAITING_SCORES && s.eraNumber() == eraNumber)
                .ifPresent(state -> {
                    eraSagaRepository.save(state.withStatus(EraSagaStatus.FAILED));
                    publishEvent(gameId, new EraFailed(gameId, state.eraNumber(), RESOLUTION_FAILED_REASON));
                    publishEvent(gameId, new GameEndedAbnormally(gameId, RESOLUTION_FAILED_REASON));
                });
    }

    private void advanceRound(EraSagaState state, ActionRoundClosed arc) {
        if (arc.roundNumber() == FINAL_ROUND) {
            eraSagaRepository.save(state.withStatus(EraSagaStatus.WAITING_SCORES));
            publishEvent(state.gameId(), new ResolutionStarted(state.gameId(), state.eraNumber()));
        } else {
            var nextRound = arc.roundNumber() + 1;
            var nextStatus = arc.roundNumber() == 1 ? EraSagaStatus.WAITING_ROUND_2 : EraSagaStatus.WAITING_ROUND_3;
            eraSagaRepository.save(state.withStatus(nextStatus));
            applicationEventPublisher.publishEvent(
                    new StartActionRoundRequested(state.gameId(), state.eraNumber(), nextRound, state.playerIds()));
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
                                    .findByIdWithLock(gameId)
                                    .orElseThrow(() -> new GameNotFoundException(gameId));
                            if (game.status() == GameStatus.ENDED_BY_COLLAPSE) {
                                eraSagaRepository.save(state.withStatus(EraSagaStatus.COMPLETED));
                                return;
                            }
                            game.endEra(gameRules.maxEras());
                            gameRepository.save(game);
                            eraSagaRepository.save(state.withStatus(EraSagaStatus.COMPLETED));

                            if (game.status() == GameStatus.ENDED_BY_STABILIZATION) {
                                var stabilized = buildTimelineStabilized(gameId, su);
                                publishEvent(gameId, stabilized);
                                applicationEventPublisher.publishEvent(stabilized);
                            } else {
                                var carryOverEvents = game.drainPendingCarryOverEvents();
                                gameRepository.save(game);
                                var nextEra = state.eraNumber() + 1;
                                publishEvent(
                                        gameId,
                                        new EraEnded(
                                                gameId, state.eraNumber(), game.cascadedParadoxCounter(), nextEra));
                                var eraStarted = new EraStarted(gameId, nextEra, carryOverEvents, state.playerIds());
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
        eventPublisher.publish(DomainEventEnvelope.create(
                gameId, Game.AGGREGATE_TYPE, gameId, DomainEventEnvelope.SCHEMA_VERSION_V1, payload, clock));
    }

    private TimelineStabilized buildTimelineStabilized(UUID gameId, ScoresUpdated su) {
        var winners = new ArrayList<TimelineStabilized.PlayerFactionResult>();
        var losers = new ArrayList<TimelineStabilized.PlayerFactionResult>();
        for (var update : su.updates()) {
            var result = new TimelineStabilized.PlayerFactionResult(
                    update.playerId(), update.faction().name(), null);
            if (gameRules.stabilizationWinnerFactions().contains(update.faction())) {
                winners.add(result);
            } else {
                losers.add(result);
            }
        }
        return new TimelineStabilized(gameId, winners, losers);
    }
}
