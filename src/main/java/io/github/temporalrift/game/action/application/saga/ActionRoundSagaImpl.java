package io.github.temporalrift.game.action.application.saga;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.action.application.ActionRoundEventPublication;
import io.github.temporalrift.game.action.domain.actionround.ActionRound;
import io.github.temporalrift.game.action.domain.actionround.ActionRoundConfig;
import io.github.temporalrift.game.action.domain.actionround.ActionRoundParticipants;
import io.github.temporalrift.game.action.domain.actionround.CloseOutcome;
import io.github.temporalrift.game.action.domain.actionround.RoundCancellation;
import io.github.temporalrift.game.action.domain.actionround.SubmittedAction;
import io.github.temporalrift.game.action.domain.activisterastate.ProbabilityInfluenceSignature;
import io.github.temporalrift.game.action.domain.event.ActionEventPayload;
import io.github.temporalrift.game.action.domain.event.ActionRoundTimerExpired;
import io.github.temporalrift.game.action.domain.event.BandedProbabilityPublished;
import io.github.temporalrift.game.action.domain.event.ExposeBehaviorChanged;
import io.github.temporalrift.game.action.domain.event.ExposeSignatureRevealed;
import io.github.temporalrift.game.action.domain.event.HandCardIntercepted;
import io.github.temporalrift.game.action.domain.event.InfluenceTraced;
import io.github.temporalrift.game.action.domain.event.PlayerJammed;
import io.github.temporalrift.game.action.domain.event.RoundSummaryPublished;
import io.github.temporalrift.game.action.domain.event.RoundSummaryPublished.ActionSummary;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.port.out.ActionEventPublisher;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;
import io.github.temporalrift.game.action.domain.port.out.ActivistEraStateRepository;
import io.github.temporalrift.game.action.domain.port.out.FutureEventDefinitionPort;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaState;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaStatus;
import io.github.temporalrift.game.shared.application.SagaHandoffPublisher;
import io.github.temporalrift.game.shared.domain.event.EraActionFactsFinalized;
import io.github.temporalrift.game.shared.domain.event.PlayersIdentified;
import io.github.temporalrift.game.shared.domain.messaging.DomainEventEnvelope;
import io.github.temporalrift.game.shared.domain.model.CardDrawWeights;
import io.github.temporalrift.game.shared.domain.model.CardType;
import io.github.temporalrift.game.shared.domain.port.out.GameRulesPort;

@Service
@ConditionalOnBean({ActionRoundRepository.class, PlayerStateRepository.class})
class ActionRoundSagaImpl implements ActionRoundSaga {

    private static final Logger log = LoggerFactory.getLogger(ActionRoundSagaImpl.class);

    private static final String CLOSE_REASON_ALL_SUBMITTED = "ALL_SUBMITTED";
    private static final String CLOSE_REASON_TIMER_EXPIRED = "TIMER_EXPIRED";
    private static final int SIGNATURE_REVEAL_ROUND_NUMBER = 2;

    private static final Random INTERCEPT_RANDOMNESS = new SecureRandom();

    // Era saga hard-caps rounds at 3 (see EraSagaAdvancer.FINAL_ROUND, session module) — this module
    // needs its own copy because it is the one computing the round boundary; scoring no longer needs
    // to know this value at all (see EraActionFactsFinalized).
    private static final int FINAL_ROUND_NUMBER = 3;

    private final ActionRoundRepository actionRoundRepository;
    private final ActivistEraStateRepository activistEraStateRepository;
    private final PlayerStateRepository playerStateRepository;
    private final ActionEventPublisher actionEventPublisher;
    private final SagaHandoffPublisher sagaHandoffPublisher;
    private final ActionRoundSagaStateManager stateManager;
    private final GameRulesPort gameRules;
    private final FutureEventDefinitionPort futureEventDefinitionPort;
    private final BandCalculator bandCalculator;
    private final ActionRoundTimerRegistry timerRegistry;
    private final Clock clock;

    ActionRoundSagaImpl(
            ActionRoundRepository actionRoundRepository,
            ActivistEraStateRepository activistEraStateRepository,
            PlayerStateRepository playerStateRepository,
            ActionEventPublisher actionEventPublisher,
            ApplicationEventPublisher applicationEventPublisher,
            ActionRoundSagaStateManager stateManager,
            GameRulesPort gameRules,
            FutureEventDefinitionPort futureEventDefinitionPort,
            BandCalculator bandCalculator,
            ActionRoundTimerRegistry timerRegistry,
            Clock clock) {
        this.actionRoundRepository = actionRoundRepository;
        this.activistEraStateRepository = activistEraStateRepository;
        this.playerStateRepository = playerStateRepository;
        this.actionEventPublisher = actionEventPublisher;
        this.sagaHandoffPublisher = new SagaHandoffPublisher(applicationEventPublisher);
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
        // Declaration handlers lock one player row before checking the Round-1 boundary. Locking
        // every player row here prevents a declaration from being accepted after we read it but
        // before the round is created: either this transaction sees it, or the handler sees Round 1.
        playerStateRepository.lockAllByGameId(gameId);
        var sagaId = UUID.randomUUID();
        var timerSeconds = gameRules.actionRoundTimerSeconds(playerIds.size());
        var timerExpiresAt = clock.instant().plusSeconds(timerSeconds);

        List<SubmittedAction> declaredActions = roundNumber == 1
                ? activistEraStateRepository.findDeclaredByGameIdAndEraNumber(gameId, eraNumber).stream()
                        .<SubmittedAction>map(state -> new SubmittedAction.SpecialActionSubmission(
                                state.activistPlayerId(),
                                io.github.temporalrift.game.shared.domain.model.Faction.ACTIVISTS,
                                state.declarationMode().toSpecialAction(),
                                null,
                                null,
                                state.targetEventId(),
                                state.targetOutcomeId(),
                                null))
                        .toList()
                : List.<SubmittedAction>of();
        var pendingPlayerIds = playerIds.stream()
                .filter(playerId -> declaredActions.stream()
                        .noneMatch(action -> action.playerId().equals(playerId)))
                .toList();
        stateManager.initWaiting(sagaId, gameId, eraNumber, roundNumber, pendingPlayerIds, timerExpiresAt);
        var round = new ActionRound(
                UUID.randomUUID(),
                new ActionRoundConfig(gameId, eraNumber, roundNumber, timerSeconds),
                new ActionRoundParticipants(playerIds, declaredActions));
        actionRoundRepository.save(round);
        ActionRoundEventPublication.publish(round, actionEventPublisher, clock);
        if (pendingPlayerIds.isEmpty()) {
            tryClose(sagaId, gameId, eraNumber, roundNumber, CLOSE_REASON_ALL_SUBMITTED);
        }
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
                var liveActions = uncancelledActionList(round);
                var identifiedPlayerIds = new LinkedHashSet<UUID>();
                identifiedPlayerIds.addAll(publishTracedInfluence(round, liveActions, gameId, eraNumber, roundNumber));
                // Must run before reconcileObscureState, while the Obscure flag still covers this round.
                identifiedPlayerIds.addAll(publishInterceptedHands(round, liveActions, gameId, eraNumber, roundNumber));
                reconcileJamState(liveActions, gameId, eraNumber, roundNumber);
                reconcileObscureState(liveActions, gameId, roundNumber);

                if (roundNumber == SIGNATURE_REVEAL_ROUND_NUMBER) {
                    publishBandedProbabilities(gameId, eraNumber, round);
                    identifiedPlayerIds.addAll(publishExposeSignatures(gameId, eraNumber, liveActions));
                }
                publishIdentifications(gameId, eraNumber, roundNumber, List.copyOf(identifiedPlayerIds));
                if (roundNumber == FINAL_ROUND_NUMBER) {
                    publishExposeBehaviorChanges(gameId, eraNumber, round);
                    publishFinalRoundActionFacts(gameId, eraNumber, round, List.copyOf(identifiedPlayerIds));
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

    private void reconcileJamState(List<SubmittedAction> liveActions, UUID gameId, int eraNumber, int roundNumber) {
        playerStateRepository.lockAllByGameId(gameId);
        var jammedPlayerIds = new LinkedHashSet<UUID>();
        if (roundNumber < FINAL_ROUND_NUMBER) {
            liveActions.stream()
                    .filter(SubmittedAction.CardAction.class::isInstance)
                    .map(SubmittedAction.CardAction.class::cast)
                    .filter(card -> card.cardType() == CardType.JAM)
                    .map(SubmittedAction.CardAction::targetPlayerId)
                    .forEach(jammedPlayerIds::add);
        }

        for (var playerState : playerStateRepository.findAllByGameId(gameId)) {
            var previouslyJammed = playerState.isJammed();
            var jammedForNextRound = jammedPlayerIds.contains(playerState.playerId());
            if (previouslyJammed) {
                playerState.clearJam();
            }
            if (jammedForNextRound) {
                playerState.applyJam();
            }
            if (previouslyJammed || jammedForNextRound) {
                playerStateRepository.save(playerState);
            }
            if (jammedForNextRound) {
                actionEventPublisher.publish(DomainEventEnvelope.create(
                        playerState.id(),
                        PlayerState.AGGREGATE_TYPE,
                        gameId,
                        DomainEventEnvelope.SCHEMA_VERSION_V1,
                        new PlayerJammed(gameId, eraNumber, playerState.playerId(), roundNumber + 1),
                        clock));
            }
        }
    }

    private void reconcileObscureState(List<SubmittedAction> liveActions, UUID gameId, int roundNumber) {
        // Obscure covers exactly one following round and never crosses an era boundary, mirroring Jam's
        // lifecycle; Intercept at that round's close reads this flag to reveal decoys instead of the hand
        // and to skip identification. Applied at round close (not at submit) so simultaneous
        // submissions in the closing round cannot observe a mid-round flag change. No separate lock:
        // reconcileJamState runs immediately before in the same transaction and already holds all rows.
        var obscuredPlayerIds = roundNumber < FINAL_ROUND_NUMBER ? obscureSubmitters(liveActions) : Set.<UUID>of();

        for (var playerState : playerStateRepository.findAllByGameId(gameId)) {
            var previouslyObscured = playerState.isObscured();
            var obscuredForNextRound = obscuredPlayerIds.contains(playerState.playerId());
            if (previouslyObscured) {
                playerState.clearObscure();
            }
            if (obscuredForNextRound) {
                playerState.applyObscure();
            }
            if (previouslyObscured || obscuredForNextRound) {
                playerStateRepository.save(playerState);
            }
        }
    }

    private Set<UUID> obscureSubmitters(List<SubmittedAction> liveActions) {
        return liveActions.stream()
                .filter(SubmittedAction.SpecialActionSubmission.class::isInstance)
                .map(SubmittedAction.SpecialActionSubmission.class::cast)
                .filter(special -> special.specialAction()
                        == io.github.temporalrift.game.shared.domain.model.SpecialAction.OBSCURE)
                .map(SubmittedAction.SpecialActionSubmission::playerId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    // The Obscure flag of a past round has already been cleared, so coverage is derived from the round before it.
    private Set<UUID> obscuredDuring(ActionRound observedRound) {
        if (observedRound.roundNumber() == 1) {
            return Set.of();
        }
        return actionRoundRepository
                .findByGameIdAndEraNumberAndRoundNumber(
                        observedRound.gameId(), observedRound.eraNumber(), observedRound.roundNumber() - 1)
                .map(ActionRoundSagaImpl::uncancelledActionList)
                .map(this::obscureSubmitters)
                .orElseGet(Set::of);
    }

    private List<UUID> publishTracedInfluence(
            ActionRound round, List<SubmittedAction> liveActions, UUID gameId, int eraNumber, int roundNumber) {
        var traces = liveActions.stream()
                .filter(SubmittedAction.CardAction.class::isInstance)
                .map(SubmittedAction.CardAction.class::cast)
                .filter(card -> card.cardType() == CardType.TRACE)
                .toList();
        if (traces.isEmpty()) {
            return List.of();
        }
        var predecessor = previousRound(gameId, eraNumber, roundNumber);
        var obscuredPlayerIds = predecessor.map(this::obscuredDuring).orElseGet(Set::of);
        var listedPlayerIds = new LinkedHashSet<UUID>();
        for (var trace : traces) {
            traceTargets(trace, gameId, eraNumber, roundNumber).forEach(targetEventId -> {
                var influencerPlayerIds =
                        predecessor.map(ActionRoundSagaImpl::uncancelledActionList).orElseGet(List::of).stream()
                                .filter(SubmittedAction.CardAction.class::isInstance)
                                .map(SubmittedAction.CardAction.class::cast)
                                .filter(card -> card.isDirectProbabilityInfluenceOn(targetEventId))
                                .map(SubmittedAction.CardAction::playerId)
                                .filter(playerId -> !obscuredPlayerIds.contains(playerId))
                                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
                listedPlayerIds.addAll(influencerPlayerIds);
                actionEventPublisher.publish(DomainEventEnvelope.create(
                        round.id(),
                        ActionRound.AGGREGATE_TYPE,
                        gameId,
                        DomainEventEnvelope.SCHEMA_VERSION_V1,
                        new InfluenceTraced(
                                gameId,
                                eraNumber,
                                roundNumber,
                                trace.playerId(),
                                targetEventId,
                                List.copyOf(influencerPlayerIds)),
                        clock));
            });
        }
        return List.copyOf(listedPlayerIds);
    }

    private List<UUID> publishInterceptedHands(
            ActionRound round, List<SubmittedAction> liveActions, UUID gameId, int eraNumber, int roundNumber) {
        var intercepts = liveActions.stream()
                .filter(SubmittedAction.CardAction.class::isInstance)
                .map(SubmittedAction.CardAction.class::cast)
                .filter(card -> card.cardType() == CardType.INTERCEPT)
                .toList();
        if (intercepts.isEmpty()) {
            return List.of();
        }
        var statesByPlayer = new HashMap<UUID, PlayerState>();
        for (var playerState : playerStateRepository.findAllByGameId(gameId)) {
            statesByPlayer.put(playerState.playerId(), playerState);
        }
        var identifiedPlayerIds = new ArrayList<UUID>();
        for (var intercept : intercepts) {
            var target = java.util.Optional.ofNullable(statesByPlayer.get(intercept.targetPlayerId()));
            var hand = target.map(PlayerState::hand).orElseGet(List::of);
            var obscured = target.filter(PlayerState::isObscured).isPresent();
            var sample = obscured
                    ? InterceptHandSampler.decoys(hand, intercept.grade(), cardDrawWeights(), INTERCEPT_RANDOMNESS)
                    : InterceptHandSampler.select(hand, intercept.grade(), INTERCEPT_RANDOMNESS);
            if (!obscured) {
                identifiedPlayerIds.add(intercept.targetPlayerId());
            }
            var revealed = sample.stream()
                    .map(card ->
                            new HandCardIntercepted.RevealedCard(card.cardInstanceId(), card.cardType(), card.grade()))
                    .toList();
            actionEventPublisher.publish(DomainEventEnvelope.create(
                    round.id(),
                    ActionRound.AGGREGATE_TYPE,
                    gameId,
                    DomainEventEnvelope.SCHEMA_VERSION_V1,
                    new HandCardIntercepted(
                            gameId, eraNumber, roundNumber, intercept.playerId(), intercept.targetPlayerId(), revealed),
                    clock));
        }
        return identifiedPlayerIds;
    }

    private CardDrawWeights cardDrawWeights() {
        return new CardDrawWeights(gameRules.cardCategoryWeights(), gameRules.cardGradeWeights());
    }

    private void publishIdentifications(UUID gameId, int eraNumber, int roundNumber, List<UUID> identifiedPlayerIds) {
        if (!identifiedPlayerIds.isEmpty()) {
            actionEventPublisher.publishInternally(
                    new PlayersIdentified(gameId, eraNumber, roundNumber, identifiedPlayerIds));
        }
    }

    private java.util.Optional<ActionRound> previousRound(UUID gameId, int eraNumber, int roundNumber) {
        if (eraNumber == 1 && roundNumber == 1) {
            return java.util.Optional.empty();
        }
        var predecessorEra = roundNumber == 1 ? eraNumber - 1 : eraNumber;
        var predecessorRound = roundNumber == 1 ? FINAL_ROUND_NUMBER : roundNumber - 1;
        return actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(gameId, predecessorEra, predecessorRound);
    }

    private List<UUID> traceTargets(SubmittedAction.CardAction trace, UUID gameId, int eraNumber, int roundNumber) {
        return switch (trace.grade()) {
            case I -> List.of(trace.targetEventId());
            case II ->
                previousRound(gameId, eraNumber, roundNumber)
                        .map(ignored ->
                                futureEventDefinitionPort
                                        .findByGameIdAndEraNumber(gameId, roundNumber == 1 ? eraNumber - 1 : eraNumber)
                                        .stream()
                                        .map(FutureEventDefinitionPort.EventDefinition::eventId)
                                        .toList())
                        .orElseGet(List::of);
            // Submission validation rejects unsupported TRACE grades; yielding no targets here
            // keeps a stray record from rolling back the round close.
            case III -> List.of();
        };
    }

    // Corrupt blind-targets a player, not a card (submissions within a round are simultaneous), so the
    // correlation to a specific card is only knowable once that round's own submittedActions are
    // complete. Computed per round (a Corrupt in round N only correlates to a card ALSO played in round
    // N) but bundled into the final round's EraActionFactsFinalized rather than published immediately —
    // see publishFinalRoundActionFacts, which applies this same round-scoped correlation across every
    // round of the era in one synchronous, non-racing pass.
    private List<EraActionFactsFinalized.CorruptCorrelationFact> correlateCorruptCardsForRound(ActionRound round) {
        var cancelledPlayerIds = RoundCancellation.cancelledPlayerIds(round.submittedActions());
        var shiftCardsByPlayer = round.submittedActions().stream()
                .filter(SubmittedAction.CardAction.class::isInstance)
                .map(SubmittedAction.CardAction.class::cast)
                .filter(card -> !cancelledPlayerIds.contains(card.playerId()))
                .filter(card -> card.cardType() == CardType.PUSH
                        || card.cardType() == CardType.SUPPRESS
                        || card.cardType() == CardType.SWING)
                .collect(java.util.stream.Collectors.toMap(SubmittedAction.CardAction::playerId, card -> card));

        var correlations = new ArrayList<EraActionFactsFinalized.CorruptCorrelationFact>();
        round.submittedActions().stream()
                .filter(SubmittedAction.SpecialActionSubmission.class::isInstance)
                .map(SubmittedAction.SpecialActionSubmission.class::cast)
                .filter(special -> !cancelledPlayerIds.contains(special.playerId()))
                .filter(special -> special.specialAction()
                        == io.github.temporalrift.game.shared.domain.model.SpecialAction.CORRUPT)
                .forEach(corrupt -> {
                    var targetCard = shiftCardsByPlayer.get(corrupt.targetPlayerId());
                    if (targetCard != null) {
                        correlations.add(new EraActionFactsFinalized.CorruptCorrelationFact(
                                corrupt.playerId(),
                                corrupt.targetPlayerId(),
                                targetCard.cardInstanceId(),
                                targetCard.targetEventId(),
                                targetCard.sourceOutcomeId(),
                                targetCard.targetOutcomeId()));
                    }
                });
        return correlations;
    }

    private void publishBandedProbabilities(UUID gameId, int eraNumber, ActionRound round2) {
        var round1 = actionRoundRepository
                .findByGameIdAndEraNumberAndRoundNumber(gameId, eraNumber, 1)
                .orElseThrow(
                        () -> new IllegalStateException("Round 1 not found for game " + gameId + " era " + eraNumber));
        var initialDefinitions = futureEventDefinitionPort.findByGameIdAndEraNumber(gameId, eraNumber);
        var bandStates = bandCalculator.computeBands(
                uncancelledActionList(round1), uncancelledActionList(round2), initialDefinitions);
        actionEventPublisher.publish(DomainEventEnvelope.create(
                round2.id(),
                ActionRound.AGGREGATE_TYPE,
                gameId,
                DomainEventEnvelope.SCHEMA_VERSION_V1,
                new BandedProbabilityPublished(gameId, eraNumber, bandStates),
                clock));
    }

    // The revealed signature is from Round 1, which no Obscure can cover, so every exposed player is identified.
    private List<UUID> publishExposeSignatures(UUID gameId, int eraNumber, List<SubmittedAction> liveActions) {
        var liveExposePlayerIds = liveActions.stream()
                .filter(SubmittedAction.SpecialActionSubmission.class::isInstance)
                .map(SubmittedAction.SpecialActionSubmission.class::cast)
                .filter(special ->
                        special.specialAction() == io.github.temporalrift.game.shared.domain.model.SpecialAction.EXPOSE)
                .map(SubmittedAction.SpecialActionSubmission::playerId)
                .collect(java.util.stream.Collectors.toSet());
        var exposedStates = activistEraStateRepository.findExposedByGameIdAndEraNumber(gameId, eraNumber).stream()
                .filter(state -> liveExposePlayerIds.contains(state.activistPlayerId()))
                .toList();
        exposedStates.forEach(state -> actionEventPublisher.publish(DomainEventEnvelope.create(
                state.id(),
                io.github.temporalrift.game.action.domain.activisterastate.ActivistEraState.AGGREGATE_TYPE,
                gameId,
                DomainEventEnvelope.SCHEMA_VERSION_V1,
                new ExposeSignatureRevealed(
                        gameId,
                        eraNumber,
                        SIGNATURE_REVEAL_ROUND_NUMBER,
                        state.activistPlayerId(),
                        state.exposedPlayerId(),
                        state.exposedSignature()),
                clock)));
        return exposedStates.stream()
                .map(io.github.temporalrift.game.action.domain.activisterastate.ActivistEraState::exposedPlayerId)
                .toList();
    }

    private void publishExposeBehaviorChanges(UUID gameId, int eraNumber, ActionRound round3) {
        var cancelledExposePlayerIds = actionRoundRepository
                .findByGameIdAndEraNumberAndRoundNumber(gameId, eraNumber, SIGNATURE_REVEAL_ROUND_NUMBER)
                .map(ActionRound::submittedActions)
                .map(RoundCancellation::cancelledPlayerIds)
                .orElseGet(Set::of);
        activistEraStateRepository.findExposedByGameIdAndEraNumber(gameId, eraNumber).stream()
                .filter(state -> !cancelledExposePlayerIds.contains(state.activistPlayerId()))
                .forEach(state -> {
                    var responseSignature = uncancelledActions(round3)
                            .filter(action -> action.playerId().equals(state.exposedPlayerId()))
                            .findFirst()
                            .flatMap(ProbabilityInfluenceSignature::from);
                    if (responseSignature.isPresent() && state.recordExposeBehaviorChanged(responseSignature.get())) {
                        activistEraStateRepository.save(state);
                        DomainEventEnvelope<ActionEventPayload> envelope = DomainEventEnvelope.create(
                                state.id(),
                                io.github.temporalrift.game.action.domain.activisterastate.ActivistEraState
                                        .AGGREGATE_TYPE,
                                gameId,
                                DomainEventEnvelope.SCHEMA_VERSION_V1,
                                new ExposeBehaviorChanged(
                                        gameId,
                                        eraNumber,
                                        FINAL_ROUND_NUMBER,
                                        state.activistPlayerId(),
                                        state.exposedPlayerId()),
                                clock);
                        sagaHandoffPublisher.publish(
                                actionEventPublisher::publish,
                                envelope,
                                new io.github.temporalrift.game.shared.domain.event.ExposeBehaviorChanged(
                                        gameId, eraNumber, state.activistPlayerId(), state.exposedPlayerId()));
                    }
                });
    }

    // In-process only, published directly (not through ActionRoundEventPublication): built from the
    // final round's own submittedActions, which is complete and final by the time close() returns, so
    // it cannot race the independently-dispatched per-submission ForesightDeclared/OutcomeAnnihilated
    // listeners the way onActionRoundClosed alone would.
    private void publishFinalRoundActionFacts(
            UUID gameId, int eraNumber, ActionRound round, List<UUID> identifiedPlayerIds) {
        var eraRounds = new ArrayList<ActionRound>();
        for (var roundNumber = 1; roundNumber < FINAL_ROUND_NUMBER; roundNumber++) {
            actionRoundRepository
                    .findByGameIdAndEraNumberAndRoundNumber(gameId, eraNumber, roundNumber)
                    .ifPresent(eraRounds::add);
        }
        eraRounds.add(round);
        var cancelledRoundOnePlayerIds = cancellationForRound(eraRounds, 1);
        var cancelledRoundTwoPlayerIds = cancellationForRound(eraRounds, SIGNATURE_REVEAL_ROUND_NUMBER);
        var foresightFacts = new ArrayList<EraActionFactsFinalized.ForesightFact>();
        var annihilationFacts = new ArrayList<EraActionFactsFinalized.AnnihilationFact>();
        var mimicFacts = new ArrayList<EraActionFactsFinalized.RevisionistFact>();
        var latestRewriteFacts = new LinkedHashMap<UUID, EraActionFactsFinalized.RevisionistFact>();
        var fulfillmentFacts = new java.util.LinkedHashSet<EraActionFactsFinalized.FulfillmentFact>();
        var exposeFacts = activistEraStateRepository.findExposedByGameIdAndEraNumber(gameId, eraNumber).stream()
                .filter(state -> !cancelledRoundTwoPlayerIds.contains(state.activistPlayerId()))
                .filter(
                        io.github.temporalrift.game.action.domain.activisterastate.ActivistEraState
                                ::exposeBehaviorChanged)
                .map(state -> new EraActionFactsFinalized.ExposeFact(state.activistPlayerId(), state.exposedPlayerId()))
                .toList();
        var activistDeclarationFacts =
                activistEraStateRepository.findDeclaredByGameIdAndEraNumber(gameId, eraNumber).stream()
                        .filter(state -> !cancelledRoundOnePlayerIds.contains(state.activistPlayerId()))
                        .map(state -> new EraActionFactsFinalized.ActivistDeclarationFact(
                                state.activistPlayerId(),
                                state.declarationMode().toSpecialAction(),
                                state.targetEventId(),
                                state.targetOutcomeId()))
                        .toList();
        var corruptCorrelationFacts = eraRounds.stream()
                .flatMap(actionRound -> correlateCorruptCardsForRound(actionRound).stream())
                .toList();
        for (var action : eraRounds.stream()
                .flatMap(ActionRoundSagaImpl::uncancelledActions)
                .toList()) {
            if (action instanceof SubmittedAction.SpecialActionSubmission special) {
                switch (special.specialAction()) {
                    case FORESIGHT ->
                        foresightFacts.add(new EraActionFactsFinalized.ForesightFact(
                                special.targetEventId(), special.targetOutcomeId(), special.playerId()));
                    case ANNIHILATE ->
                        annihilationFacts.add(new EraActionFactsFinalized.AnnihilationFact(
                                special.targetEventId(), special.targetOutcomeId(), special.playerId()));
                    // The latest Rewrite is the player's single era declaration; it replaces any earlier one.
                    case REWRITE ->
                        latestRewriteFacts.put(
                                special.playerId(),
                                new EraActionFactsFinalized.RevisionistFact(
                                        special.playerId(),
                                        special.specialAction(),
                                        special.targetEventId(),
                                        special.targetOutcomeId()));
                    case MIMIC ->
                        mimicFacts.add(new EraActionFactsFinalized.RevisionistFact(
                                special.playerId(),
                                special.specialAction(),
                                special.targetEventId(),
                                special.targetOutcomeId()));
                    case FULFILLMENT ->
                        fulfillmentFacts.add(new EraActionFactsFinalized.FulfillmentFact(
                                special.playerId(), special.targetEventId()));
                    default -> {
                        // Every other special action has no scoring-context fact to bundle.
                    }
                }
            }
        }
        actionEventPublisher.publishInternally(new EraActionFactsFinalized(
                gameId,
                eraNumber,
                foresightFacts,
                annihilationFacts,
                exposeFacts,
                activistDeclarationFacts,
                java.util.stream.Stream.concat(latestRewriteFacts.values().stream(), mimicFacts.stream())
                        .toList(),
                List.copyOf(fulfillmentFacts),
                corruptCorrelationFacts,
                identifiedPlayerIds));
    }

    private static java.util.stream.Stream<SubmittedAction> uncancelledActions(ActionRound round) {
        return uncancelledActionList(round).stream();
    }

    private static List<SubmittedAction> uncancelledActionList(ActionRound round) {
        var cancelledPlayerIds = RoundCancellation.cancelledPlayerIds(round.submittedActions());
        return round.submittedActions().stream()
                .filter(action -> !cancelledPlayerIds.contains(action.playerId()))
                .toList();
    }

    private static Set<UUID> cancellationForRound(List<ActionRound> eraRounds, int roundNumber) {
        return eraRounds.stream()
                .filter(actionRound -> actionRound.roundNumber() == roundNumber)
                .findFirst()
                .map(ActionRound::submittedActions)
                .map(RoundCancellation::cancelledPlayerIds)
                .orElseGet(Set::of);
    }
}
