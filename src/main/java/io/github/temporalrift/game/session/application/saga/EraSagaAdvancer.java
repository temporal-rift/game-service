package io.github.temporalrift.game.session.application.saga;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import io.github.temporalrift.game.session.domain.port.out.SessionFactionObjectivePort;
import io.github.temporalrift.game.session.domain.port.out.SessionGameRulesPort;
import io.github.temporalrift.game.session.domain.saga.EraSagaState;
import io.github.temporalrift.game.session.domain.saga.EraSagaStatus;
import io.github.temporalrift.game.shared.application.SagaHandoffPublisher;
import io.github.temporalrift.game.shared.domain.event.ActionRoundClosed;
import io.github.temporalrift.game.shared.domain.event.ScoresUpdated;
import io.github.temporalrift.game.shared.domain.event.StartActionRoundRequested;
import io.github.temporalrift.game.shared.domain.messaging.DomainEventEnvelope;
import io.github.temporalrift.game.shared.domain.model.Faction;

@Component
class EraSagaAdvancer {

    private static final int FINAL_ROUND = 3;
    private static final String RESOLUTION_FAILED_REASON = "resolution-failed";

    private final EraSagaRepository eraSagaRepository;
    private final EraSagaScoresUpdatedInboxRepository scoresUpdatedInbox;
    private final GameRepository gameRepository;
    private final SessionEventPublisher eventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final SagaHandoffPublisher sagaHandoffPublisher;
    private final SessionGameRulesPort gameRules;
    private final SessionFactionObjectivePort factionObjectives;
    private final Clock clock;

    EraSagaAdvancer(
            EraSagaRepository eraSagaRepository,
            EraSagaScoresUpdatedInboxRepository scoresUpdatedInbox,
            GameRepository gameRepository,
            SessionEventPublisher eventPublisher,
            ApplicationEventPublisher applicationEventPublisher,
            SessionGameRulesPort gameRules,
            SessionFactionObjectivePort factionObjectives,
            Clock clock) {
        this.eraSagaRepository = eraSagaRepository;
        this.scoresUpdatedInbox = scoresUpdatedInbox;
        this.gameRepository = gameRepository;
        this.eventPublisher = eventPublisher;
        this.applicationEventPublisher = applicationEventPublisher;
        this.sagaHandoffPublisher = new SagaHandoffPublisher(applicationEventPublisher);
        this.gameRules = gameRules;
        this.factionObjectives = factionObjectives;
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

    // Returns whether this call actually advanced the saga, so EraSagaScoresUpdatedSweep can tell a
    // real recovery apart from a no-op caused by a concurrent sweep pass or listener redelivery
    // already having advanced (or claimed) this same era first.
    @Transactional(propagation = REQUIRES_NEW)
    boolean handleScoresUpdated(UUID gameId, ScoresUpdated su) {
        // Recorded durably before the status check below: ActionRoundClosed (round 3) sets
        // WAITING_SCORES and ScoresUpdated fires from an independent async chain (timeline-service
        // resolution -> scoring), so either can arrive first. If this one loses the race, the record
        // lets EraSagaScoresUpdatedSweep complete the transition later without a second delivery.
        scoresUpdatedInbox.save(su);
        return eraSagaRepository
                .findByGameIdWithLock(gameId)
                .filter(s -> s.status() == EraSagaStatus.WAITING_SCORES && s.eraNumber() == su.eraNumber())
                .map(state -> {
                    processScoresUpdated(gameId, state, su);
                    return true;
                })
                .orElse(false);
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
        var qualifiers = findQualifiers(gameId, su);
        if (!qualifiers.isEmpty()) {
            // Mirrors the collapse/stabilization branch below: the aggregate is transitioned
            // and saved here, at detection time, so EndGameSagaImpl never mutates Game itself
            // -- it only reads the already-correct terminal status for every trigger alike.
            var game = gameRepository.findByIdWithLock(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
            // A concurrent paradox-resolution collapse (a separate saga entirely) can win the
            // lock on this same aggregate first: same race the no-winner branch below already
            // guards against, just from the opposite trigger. Without this check, game.end()
            // would throw GameAlreadyOverException uncaught and this era saga would never
            // reach COMPLETED.
            if (game.status() == GameStatus.ENDED_BY_COLLAPSE) {
                eraSagaRepository.save(state.withStatus(EraSagaStatus.COMPLETED));
                return;
            }
            game.end();
            gameRepository.save(game);
            eraSagaRepository.save(state.withStatus(EraSagaStatus.COMPLETED));
            // Shared victory: one fact per qualifier on the unchanged single-winner shape, so
            // the authoritative winner set is the set of WinConditionMet facts for this game.
            for (var qualifier : qualifiers) {
                var winConditionMet = new WinConditionMet(
                        gameId,
                        qualifier.playerId(),
                        qualifier.faction().name(),
                        qualifier.newTotal(),
                        qualifier.winType());
                sagaHandoffPublisher.publish(eventPublisher::publish, envelope(gameId, winConditionMet));
            }
            return;
        }
        var game = gameRepository.findByIdWithLock(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
        if (game.status() == GameStatus.ENDED_BY_COLLAPSE) {
            eraSagaRepository.save(state.withStatus(EraSagaStatus.COMPLETED));
            return;
        }
        game.endEra(gameRules.maxEras());
        gameRepository.save(game);
        eraSagaRepository.save(state.withStatus(EraSagaStatus.COMPLETED));

        if (game.status() == GameStatus.ENDED_BY_STABILIZATION) {
            var stabilized = buildTimelineStabilized(gameId, su);
            sagaHandoffPublisher.publish(eventPublisher::publish, envelope(gameId, stabilized));
        } else {
            var carryOverEvents = game.drainPendingCarryOverEvents();
            gameRepository.save(game);
            var nextEra = state.eraNumber() + 1;
            publishEvent(gameId, new EraEnded(gameId, state.eraNumber(), game.cascadedParadoxCounter(), nextEra));
            var eraStarted = new EraStarted(gameId, nextEra, carryOverEvents, state.playerIds());
            sagaHandoffPublisher.publish(eventPublisher::publish, envelope(gameId, eraStarted));
        }
    }

    private record Qualifier(UUID playerId, Faction faction, int newTotal, String winType) {}

    private List<Qualifier> findQualifiers(UUID gameId, ScoresUpdated su) {
        var metByPlayer = new HashMap<UUID, Boolean>();
        for (var progress : factionObjectives.evaluate(gameId, su.eraNumber())) {
            metByPlayer.put(progress.playerId(), progress.objectiveMet());
        }
        var qualifiers = new ArrayList<Qualifier>();
        for (var update : su.updates()) {
            boolean thresholdMet = update.newTotal() >= gameRules.winScoreThreshold();
            boolean objectiveMet = Boolean.TRUE.equals(metByPlayer.get(update.playerId()));
            if (thresholdMet || objectiveMet) {
                qualifiers.add(new Qualifier(
                        update.playerId(),
                        update.faction(),
                        update.newTotal(),
                        thresholdMet ? "SCORE_THRESHOLD" : "FACTION_OBJECTIVE"));
            }
        }
        qualifiers.sort(Comparator.comparing(Qualifier::playerId));
        return qualifiers;
    }

    private void publishEvent(UUID gameId, Object payload) {
        eventPublisher.publish(envelope(gameId, payload));
    }

    private <T> DomainEventEnvelope<T> envelope(UUID gameId, T payload) {
        return DomainEventEnvelope.create(
                gameId, Game.AGGREGATE_TYPE, gameId, DomainEventEnvelope.SCHEMA_VERSION_V1, payload, clock);
    }

    private TimelineStabilized buildTimelineStabilized(UUID gameId, ScoresUpdated su) {
        var qualifiedWeavers = new HashSet<UUID>();
        var weaverChainLength = new HashMap<UUID, Integer>();
        for (var progress : factionObjectives.evaluate(gameId, su.eraNumber())) {
            if (progress.faction() == Faction.WEAVERS) {
                weaverChainLength.put(progress.playerId(), progress.threshold());
                if (progress.objectiveMet()) {
                    qualifiedWeavers.add(progress.playerId());
                }
            }
        }
        var winners = new ArrayList<TimelineStabilized.PlayerFactionResult>();
        var losers = new ArrayList<TimelineStabilized.PlayerFactionResult>();
        for (var update : su.updates()) {
            Integer activeChainLength = weaverChainLength.containsKey(update.playerId())
                    ? (qualifiedWeavers.contains(update.playerId()) ? weaverChainLength.get(update.playerId()) : null)
                    : null;
            var result = new TimelineStabilized.PlayerFactionResult(
                    update.playerId(), update.faction().name(), activeChainLength);
            if (isStabilizationWinner(update, qualifiedWeavers)) {
                winners.add(result);
            } else {
                losers.add(result);
            }
        }
        return new TimelineStabilized(gameId, winners, losers);
    }

    private boolean isStabilizationWinner(ScoresUpdated.ScoreUpdate update, Set<UUID> qualifiedWeavers) {
        if (!gameRules.stabilizationWinnerFactions().contains(update.faction())) {
            return false;
        }
        // Weavers win stabilization only with a qualifying active chain; other allowlisted
        // factions keep the configured behavior.
        if (update.faction() == Faction.WEAVERS) {
            return qualifiedWeavers.contains(update.playerId());
        }
        return true;
    }
}
