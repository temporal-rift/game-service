package io.github.temporalrift.game.action.application.saga;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.github.temporalrift.events.action.ActionRoundClosed;
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
import io.github.temporalrift.game.action.domain.port.out.GameRulesPort;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaStatus;

@Service
class ActionRoundSagaImpl implements ActionRoundSaga {

    private static final Logger log = LoggerFactory.getLogger(ActionRoundSagaImpl.class);

    private final ActionRoundRepository actionRoundRepository;
    private final ActionEventPublisher actionEventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ActionRoundSagaStateManager stateManager;
    private final GameRulesPort gameRules;
    private final FutureEventDefinitionPort futureEventDefinitionPort;
    private final BandCalculator bandCalculator;
    private final TaskScheduler taskScheduler;
    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> scheduledTimers;

    ActionRoundSagaImpl(
            ActionRoundRepository actionRoundRepository,
            ActionEventPublisher actionEventPublisher,
            ApplicationEventPublisher applicationEventPublisher,
            ActionRoundSagaStateManager stateManager,
            GameRulesPort gameRules,
            FutureEventDefinitionPort futureEventDefinitionPort,
            BandCalculator bandCalculator,
            @Qualifier("actionTaskScheduler") TaskScheduler taskScheduler) {
        this.actionRoundRepository = actionRoundRepository;
        this.actionEventPublisher = actionEventPublisher;
        this.applicationEventPublisher = applicationEventPublisher;
        this.stateManager = stateManager;
        this.gameRules = gameRules;
        this.futureEventDefinitionPort = futureEventDefinitionPort;
        this.bandCalculator = bandCalculator;
        this.taskScheduler = taskScheduler;
        this.scheduledTimers = new ConcurrentHashMap<>();
    }

    @Override
    @Transactional(propagation = REQUIRES_NEW)
    public void start(UUID gameId, int eraNumber, int roundNumber, List<UUID> playerIds) {
        var sagaId = UUID.randomUUID();
        var timerSeconds = gameRules.actionRoundTimerSeconds(playerIds.size());
        var timerExpiresAt = Instant.now().plusSeconds(timerSeconds);

        stateManager.initWaiting(sagaId, gameId, eraNumber, roundNumber, playerIds, timerExpiresAt);
        scheduleAfterCommit(sagaId, timerExpiresAt);
    }

    @Override
    @Transactional(propagation = REQUIRES_NEW)
    public void handlePlayerSubmitted(UUID gameId, int eraNumber, int roundNumber, UUID playerId) {
        var updatedState = stateManager.markSubmitted(gameId, eraNumber, roundNumber, playerId);
        if (!updatedState.pendingPlayerIds().isEmpty()) {
            return;
        }
        tryClose(gameId, eraNumber, roundNumber, "ALL_SUBMITTED");
    }

    @Override
    @Transactional(propagation = REQUIRES_NEW)
    public void handleTimerExpiry(UUID sagaId) {
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
        scheduledTimers.remove(sagaId);
        tryClose(state.gameId(), state.eraNumber(), state.roundNumber(), "TIMER_EXPIRED");
    }

    private void tryClose(UUID gameId, int eraNumber, int roundNumber, String closeReason) {
        stateManager.markClosing(gameId, eraNumber, roundNumber);

        var roundId = actionRoundRepository
                .findByGameIdAndEraNumberAndRoundNumber(gameId, eraNumber, roundNumber)
                .orElseThrow(() -> new IllegalStateException(
                        "ActionRound not found for game " + gameId + " era " + eraNumber + " round " + roundNumber))
                .id();

        var round = actionRoundRepository
                .findByIdForUpdate(roundId)
                .orElseThrow(() -> new IllegalStateException("ActionRound " + roundId + " not found for update"));

        var outcome = round.close(closeReason);

        switch (outcome) {
            case CloseOutcome.AlreadyClosing ignored -> {
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

                var closedPayload = new ActionRoundClosed(
                        gameId,
                        eraNumber,
                        roundNumber,
                        closeReason,
                        round.submittedActions().size());
                applicationEventPublisher.publishEvent(closedPayload);

                publishRoundSummary(round, gameId, eraNumber, roundNumber, skippedPlayerIds);

                if (roundNumber == 2) {
                    publishBandedProbabilities(gameId, eraNumber, round);
                }

                stateManager.complete(gameId, eraNumber, roundNumber);
            }
        }
    }

    private void publishRoundSummary(
            ActionRound round, UUID gameId, int eraNumber, int roundNumber, List<UUID> skippedPlayerIds) {
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

    void rescheduleTimer(UUID sagaId, Instant timerExpiresAt) {
        var future = taskScheduler.schedule(() -> handleTimerExpiry(sagaId), timerExpiresAt);
        if (future != null) {
            scheduledTimers.put(sagaId, future);
        }
    }

    private void scheduleAfterCommit(UUID sagaId, Instant timerExpiresAt) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    rescheduleTimer(sagaId, timerExpiresAt);
                }
            });
        } else {
            rescheduleTimer(sagaId, timerExpiresAt);
        }
    }
}
