package io.github.temporalrift.game.action.application.saga;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.action.application.ActionRoundEventPublication;
import io.github.temporalrift.game.action.domain.actionround.ActionRound;
import io.github.temporalrift.game.action.domain.actionround.CloseOutcome;
import io.github.temporalrift.game.action.domain.actionround.SubmittedAction;
import io.github.temporalrift.game.action.domain.event.ActionRoundTimerExpired;
import io.github.temporalrift.game.action.domain.event.BandedProbabilityPublished;
import io.github.temporalrift.game.action.domain.event.RoundSummaryPublished;
import io.github.temporalrift.game.action.domain.event.RoundSummaryPublished.ActionSummary;
import io.github.temporalrift.game.action.domain.port.out.ActionEventPublisher;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;
import io.github.temporalrift.game.action.domain.port.out.FutureEventDefinitionPort;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaState;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaStatus;
import io.github.temporalrift.game.shared.DomainEventEnvelope;
import io.github.temporalrift.game.shared.EraActionFactsFinalized;
import io.github.temporalrift.game.shared.GameRulesPort;

@Service
@ConditionalOnBean({ActionRoundRepository.class, PlayerStateRepository.class})
class ActionRoundSagaImpl implements ActionRoundSaga {

    private static final Logger log = LoggerFactory.getLogger(ActionRoundSagaImpl.class);

    private static final String CLOSE_REASON_ALL_SUBMITTED = "ALL_SUBMITTED";
    private static final String CLOSE_REASON_TIMER_EXPIRED = "TIMER_EXPIRED";

    // Era saga hard-caps rounds at 3 (see EraSagaAdvancer.FINAL_ROUND, session module) — this module
    // needs its own copy because it is the one computing the round boundary; scoring no longer needs
    // to know this value at all (see EraActionFactsFinalized).
    private static final int FINAL_ROUND_NUMBER = 3;

    private final ActionRoundRepository actionRoundRepository;
    private final ActionEventPublisher actionEventPublisher;
    private final ActionRoundSagaStateManager stateManager;
    private final GameRulesPort gameRules;
    private final FutureEventDefinitionPort futureEventDefinitionPort;
    private final BandCalculator bandCalculator;
    private final ActionRoundTimerRegistry timerRegistry;
    private final Clock clock;

    ActionRoundSagaImpl(
            ActionRoundRepository actionRoundRepository,
            ActionEventPublisher actionEventPublisher,
            ActionRoundSagaStateManager stateManager,
            GameRulesPort gameRules,
            FutureEventDefinitionPort futureEventDefinitionPort,
            BandCalculator bandCalculator,
            ActionRoundTimerRegistry timerRegistry,
            Clock clock) {
        this.actionRoundRepository = actionRoundRepository;
        this.actionEventPublisher = actionEventPublisher;
        this.stateManager = stateManager;
        this.gameRules = gameRules;
        this.futureEventDefinitionPort = futureEventDefinitionPort;
        this.bandCalculator = bandCalculator;
        this.timerRegistry = timerRegistry;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = REQUIRES_NEW)
    public StartResult start(UUID gameId, int eraNumber, int roundNumber, List<UUID> playerIds) {
        var sagaId = UUID.randomUUID();
        var timerSeconds = gameRules.actionRoundTimerSeconds(playerIds.size());
        var timerExpiresAt = clock.instant().plusSeconds(timerSeconds);

        stateManager.initWaiting(sagaId, gameId, eraNumber, roundNumber, playerIds, timerExpiresAt);
        var round = new ActionRound(UUID.randomUUID(), gameId, eraNumber, roundNumber, playerIds, timerSeconds);
        actionRoundRepository.save(round);
        ActionRoundEventPublication.publish(round, actionEventPublisher, clock);
        return new StartResult(sagaId, timerExpiresAt);
    }

    @Override
    @Transactional(propagation = REQUIRES_NEW)
    public void handlePlayerSubmitted(UUID gameId, int eraNumber, int roundNumber, UUID playerId) {
        // A missing saga yields Optional.empty() and must never be treated as "all submitted": only an
        // existing saga whose pending list is now empty may trigger the ALL_SUBMITTED close.
        stateManager
                .markSubmitted(gameId, eraNumber, roundNumber, playerId)
                .filter(state -> state.pendingPlayerIds().isEmpty())
                .ifPresent(
                        state -> tryClose(state.sagaId(), gameId, eraNumber, roundNumber, CLOSE_REASON_ALL_SUBMITTED));
    }

    void handleTimerExpiry(UUID sagaId) {
        stateManager
                .findBySagaId(sagaId)
                .ifPresentOrElse(
                        this::closeExpiredRound,
                        () -> log.debug("handleTimerExpiry: saga {} not found (stale or duplicate fire)", sagaId));
    }

    private void closeExpiredRound(ActionRoundSagaState state) {
        if (state.status() == ActionRoundSagaStatus.COMPLETED) {
            log.debug("handleTimerExpiry: saga {} already COMPLETED", state.sagaId());
            return;
        }
        tryClose(state.sagaId(), state.gameId(), state.eraNumber(), state.roundNumber(), CLOSE_REASON_TIMER_EXPIRED);
    }

    private void tryClose(UUID sagaId, UUID gameId, int eraNumber, int roundNumber, String closeReason) {
        // markClosing joins this transaction, so CLOSING is never durably visible on its own — a
        // crash mid-close rolls everything back to WAITING and the timer sweep retries at expiry.
        // Its value is the saga-row lock it takes, which serializes concurrent closers before the
        // round lock.
        stateManager.markClosing(gameId, eraNumber, roundNumber);

        var round = actionRoundRepository
                .findByGameIdAndEraNumberAndRoundNumberWithLock(gameId, eraNumber, roundNumber)
                .orElseThrow(() -> new IllegalStateException(
                        "ActionRound not found for game " + gameId + " era " + eraNumber + " round " + roundNumber));

        var outcome = round.close(closeReason);

        switch (outcome) {
            case CloseOutcome.AlreadyClosing _ -> {
                // Another path won the race and already moved the round out of OPEN. The saga still
                // needs to transition to COMPLETED so recovery does not retry forever.
                log.debug("tryClose: ActionRound {} already closing", round.id());
                stateManager.complete(gameId, eraNumber, roundNumber);
            }
            case CloseOutcome.Closed(var skippedPlayerIds) -> {
                if (closeReason.equals(CLOSE_REASON_TIMER_EXPIRED)) {
                    actionEventPublisher.publish(DomainEventEnvelope.create(
                            round.id(),
                            ActionRound.AGGREGATE_TYPE,
                            gameId,
                            DomainEventEnvelope.SCHEMA_VERSION_V1,
                            new ActionRoundTimerExpired(gameId, eraNumber, roundNumber, skippedPlayerIds),
                            clock));
                }

                actionRoundRepository.save(round);
                ActionRoundEventPublication.publish(round, actionEventPublisher, clock);

                publishRoundSummary(round, gameId, eraNumber, roundNumber, skippedPlayerIds);

                if (roundNumber == 2) {
                    publishBandedProbabilities(gameId, eraNumber, round);
                }
                if (roundNumber == FINAL_ROUND_NUMBER) {
                    publishFinalRoundActionFacts(gameId, eraNumber, round);
                }

                stateManager.complete(gameId, eraNumber, roundNumber);
                // Best-effort: an all-submitted close no longer leaves the in-memory timer to fire
                // a pointless expiry later. If this transaction rolls back after the cancel, the
                // database sweep still closes the round at expiry.
                timerRegistry.cancel(sagaId);
            }
        }
    }

    private void publishRoundSummary(
            ActionRound round, UUID gameId, int eraNumber, int roundNumber, List<UUID> skippedPlayerIds) {
        // Summary publication is intentionally separate from aggregate domain events because it is a
        // projection-style public view of the round, not part of the aggregate's invariant changes.
        var summaries = new ArrayList<ActionSummary>();
        for (var action : round.submittedActions()) {
            switch (action) {
                case SubmittedAction.CardAction card ->
                    summaries.add(
                            new ActionSummary(card.playerId(), card.cardType().name(), "CARD", false));
                case SubmittedAction.SpecialActionSubmission special ->
                    summaries.add(new ActionSummary(special.playerId(), "SPECIAL", "SPECIAL", false));
            }
        }
        for (var skippedId : skippedPlayerIds) {
            summaries.add(new ActionSummary(skippedId, null, null, true));
        }
        actionEventPublisher.publish(DomainEventEnvelope.create(
                round.id(),
                ActionRound.AGGREGATE_TYPE,
                gameId,
                DomainEventEnvelope.SCHEMA_VERSION_V1,
                new RoundSummaryPublished(gameId, eraNumber, roundNumber, summaries),
                clock));
    }

    private void publishBandedProbabilities(UUID gameId, int eraNumber, ActionRound round2) {
        var round1 = actionRoundRepository
                .findByGameIdAndEraNumberAndRoundNumber(gameId, eraNumber, 1)
                .orElseThrow(
                        () -> new IllegalStateException("Round 1 not found for game " + gameId + " era " + eraNumber));
        var initialDefinitions = futureEventDefinitionPort.findByGameIdAndEraNumber(gameId, eraNumber);
        var bandStates =
                bandCalculator.computeBands(round1.submittedActions(), round2.submittedActions(), initialDefinitions);
        actionEventPublisher.publish(DomainEventEnvelope.create(
                round2.id(),
                ActionRound.AGGREGATE_TYPE,
                gameId,
                DomainEventEnvelope.SCHEMA_VERSION_V1,
                new BandedProbabilityPublished(gameId, eraNumber, bandStates),
                clock));
    }

    // In-process only, published directly (not through ActionRoundEventPublication): built from the
    // final round's own submittedActions, which is complete and final by the time close() returns, so
    // it cannot race the independently-dispatched per-submission ForesightDeclared/OutcomeAnnihilated
    // listeners the way onActionRoundClosed alone would.
    private void publishFinalRoundActionFacts(UUID gameId, int eraNumber, ActionRound round) {
        var foresightFacts = new ArrayList<EraActionFactsFinalized.ForesightFact>();
        var annihilationFacts = new ArrayList<EraActionFactsFinalized.AnnihilationFact>();
        for (var action : round.submittedActions()) {
            if (action instanceof SubmittedAction.SpecialActionSubmission special) {
                switch (special.specialAction()) {
                    case FORESIGHT ->
                        foresightFacts.add(new EraActionFactsFinalized.ForesightFact(
                                special.targetEventId(), special.targetOutcomeId(), special.playerId()));
                    case ANNIHILATE ->
                        annihilationFacts.add(new EraActionFactsFinalized.AnnihilationFact(
                                special.targetEventId(), special.targetOutcomeId(), special.playerId()));
                    default -> {
                        // Every other special action has no scoring-context fact to bundle.
                    }
                }
            }
        }
        actionEventPublisher.publishInternally(
                new EraActionFactsFinalized(gameId, eraNumber, foresightFacts, annihilationFacts));
    }
}
