package io.github.temporalrift.game.action.application.saga;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.events.action.ActionRoundTimerExpired;
import io.github.temporalrift.events.action.RoundSummaryPublished;
import io.github.temporalrift.events.action.RoundSummaryPublished.ActionSummary;
import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.events.timeline.BandedProbabilityPublished;
import io.github.temporalrift.game.action.domain.actionround.ActionRound;
import io.github.temporalrift.game.action.domain.actionround.CloseOutcome;
import io.github.temporalrift.game.action.domain.actionround.SubmittedAction;
import io.github.temporalrift.game.action.domain.port.out.ActionEventPublisher;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;
import io.github.temporalrift.game.action.domain.port.out.FutureEventDefinitionPort;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaStatus;
import io.github.temporalrift.game.shared.GameRulesPort;

@Service
@ConditionalOnBean({ActionRoundRepository.class, PlayerStateRepository.class})
class ActionRoundSagaImpl implements ActionRoundSaga {

    private static final Logger log = LoggerFactory.getLogger(ActionRoundSagaImpl.class);

    private final ActionRoundRepository actionRoundRepository;
    private final ActionEventPublisher actionEventPublisher;
    private final ActionRoundSagaStateManager stateManager;
    private final GameRulesPort gameRules;
    private final FutureEventDefinitionPort futureEventDefinitionPort;
    private final BandCalculator bandCalculator;

    ActionRoundSagaImpl(
            ActionRoundRepository actionRoundRepository,
            ActionEventPublisher actionEventPublisher,
            ActionRoundSagaStateManager stateManager,
            GameRulesPort gameRules,
            FutureEventDefinitionPort futureEventDefinitionPort,
            BandCalculator bandCalculator) {
        this.actionRoundRepository = actionRoundRepository;
        this.actionEventPublisher = actionEventPublisher;
        this.stateManager = stateManager;
        this.gameRules = gameRules;
        this.futureEventDefinitionPort = futureEventDefinitionPort;
        this.bandCalculator = bandCalculator;
    }

    @Override
    @Transactional(propagation = REQUIRES_NEW)
    public StartResult start(UUID gameId, int eraNumber, int roundNumber, List<UUID> playerIds) {
        var sagaId = UUID.randomUUID();
        var timerSeconds = gameRules.actionRoundTimerSeconds(playerIds.size());
        var timerExpiresAt = Instant.now().plusSeconds(timerSeconds);

        stateManager.initWaiting(sagaId, gameId, eraNumber, roundNumber, playerIds, timerExpiresAt);
        var round = new ActionRound(UUID.randomUUID(), gameId, eraNumber, roundNumber, playerIds, timerSeconds);
        actionRoundRepository.save(round);
        publishRoundDomainEvents(round);
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
                .ifPresent(state -> tryClose(gameId, eraNumber, roundNumber, "ALL_SUBMITTED"));
    }

    void handleTimerExpiry(UUID sagaId) {
        var stateOpt = stateManager.findBySagaId(sagaId);
        if (stateOpt.isEmpty()) {
            log.debug("handleTimerExpiry: saga {} not found (stale or duplicate fire)", sagaId);
            return;
        }
        var state = stateOpt.get();
        if (state.status() == ActionRoundSagaStatus.COMPLETED) {
            log.debug("handleTimerExpiry: saga {} already COMPLETED", sagaId);
            return;
        }
        tryClose(state.gameId(), state.eraNumber(), state.roundNumber(), "TIMER_EXPIRED");
    }

    private void tryClose(UUID gameId, int eraNumber, int roundNumber, String closeReason) {
        // Marking the saga as CLOSING before taking the round lock gives recovery code a durable
        // breadcrumb. If the process dies mid-close, startup recovery can resume from that state.
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
                if (closeReason.equals("TIMER_EXPIRED")) {
                    actionEventPublisher.publish(EventEnvelope.create(
                            round.id(),
                            ActionRound.AGGREGATE_TYPE,
                            gameId,
                            1,
                            new ActionRoundTimerExpired(gameId, eraNumber, roundNumber, skippedPlayerIds)));
                }

                actionRoundRepository.save(round);
                publishRoundDomainEvents(round);

                publishRoundSummary(round, gameId, eraNumber, roundNumber, skippedPlayerIds);

                if (roundNumber == 2) {
                    publishBandedProbabilities(gameId, eraNumber, round);
                }

                stateManager.complete(gameId, eraNumber, roundNumber);
            }
        }
    }

    private void publishRoundDomainEvents(ActionRound round) {
        // The aggregate is the single source of truth for CardPlayed, SpecialActionPlayed,
        // ActionRoundStarted, PlayerSkipped, and ActionRoundClosed. Re-publishing those payloads from
        // application code would duplicate logic and eventually drift from aggregate behavior.
        for (var payload : round.pullEvents()) {
            actionEventPublisher.publish(
                    EventEnvelope.create(round.id(), ActionRound.AGGREGATE_TYPE, round.gameId(), 1, payload));
            actionEventPublisher.publishInternally(payload);
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
        actionEventPublisher.publish(EventEnvelope.create(
                round.id(),
                ActionRound.AGGREGATE_TYPE,
                gameId,
                1,
                new RoundSummaryPublished(gameId, eraNumber, roundNumber, summaries)));
    }

    private void publishBandedProbabilities(UUID gameId, int eraNumber, ActionRound round2) {
        var round1 = actionRoundRepository
                .findByGameIdAndEraNumberAndRoundNumber(gameId, eraNumber, 1)
                .orElseThrow(
                        () -> new IllegalStateException("Round 1 not found for game " + gameId + " era " + eraNumber));
        var initialDefinitions = futureEventDefinitionPort.findByGameIdAndEraNumber(gameId, eraNumber);
        var bandStates =
                bandCalculator.computeBands(round1.submittedActions(), round2.submittedActions(), initialDefinitions);
        actionEventPublisher.publish(EventEnvelope.create(
                round2.id(),
                ActionRound.AGGREGATE_TYPE,
                gameId,
                1,
                new BandedProbabilityPublished(gameId, eraNumber, bandStates)));
    }
}
