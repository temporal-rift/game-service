package io.github.temporalrift.game.action.application.saga;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
import io.github.temporalrift.game.shared.domain.event.EraActionFactsFinalized;
import io.github.temporalrift.game.shared.domain.messaging.DomainEventEnvelope;
import io.github.temporalrift.game.shared.domain.model.CardDrawWeights;
import io.github.temporalrift.game.shared.domain.model.CardType;
import io.github.temporalrift.game.shared.domain.model.EntropyCoordinate;
import io.github.temporalrift.game.shared.domain.model.EntropyPurpose;
import io.github.temporalrift.game.shared.domain.model.IdentityKind;
import io.github.temporalrift.game.shared.domain.port.out.ExecutionEntropy;
import io.github.temporalrift.game.shared.domain.port.out.GameRulesPort;

@Service
@ConditionalOnBean({ActionRoundRepository.class, PlayerStateRepository.class})
class ActionRoundSagaImpl implements ActionRoundSaga {

    private static final Logger log = LoggerFactory.getLogger(ActionRoundSagaImpl.class);

    private static final String CLOSE_REASON_ALL_SUBMITTED = "ALL_SUBMITTED";
    private static final String CLOSE_REASON_TIMER_EXPIRED = "TIMER_EXPIRED";
    private static final int SIGNATURE_REVEAL_ROUND_NUMBER = 2;

    // Era saga hard-caps rounds at 3 (see EraSagaAdvancer.FINAL_ROUND, session module) — this module
    // needs its own copy because it is the one computing the round boundary; scoring no longer needs
    // to know this value at all (see EraActionFactsFinalized).
    private static final int FINAL_ROUND_NUMBER = 3;

    private final ActionRoundRepository actionRoundRepository;
    private final ActivistEraStateRepository activistEraStateRepository;
    private final PlayerStateRepository playerStateRepository;
    private final ActionEventPublisher actionEventPublisher;
    private final ActionRoundSagaStateManager stateManager;
    private final GameRulesPort gameRules;
    private final TraceResolver traceResolver;
    private final ActionRoundTimerRegistry timerRegistry;
    private final ExecutionEntropy entropy;
    private final Clock clock;

    ActionRoundSagaImpl(
            ActionRoundRepository actionRoundRepository,
            ActivistEraStateRepository activistEraStateRepository,
            PlayerStateRepository playerStateRepository,
            ActionEventPublisher actionEventPublisher,
            ActionRoundSagaStateManager stateManager,
            GameRulesPort gameRules,
            FutureEventDefinitionPort futureEventDefinitionPort,
            ActionRoundTimerRegistry timerRegistry,
            ExecutionEntropy entropy,
            Clock clock) {
        this.actionRoundRepository = actionRoundRepository;
        this.activistEraStateRepository = activistEraStateRepository;
        this.playerStateRepository = playerStateRepository;
        this.actionEventPublisher = actionEventPublisher;
        this.stateManager = stateManager;
        this.gameRules = gameRules;
        this.traceResolver = new TraceResolver(actionRoundRepository, futureEventDefinitionPort);
        this.timerRegistry = timerRegistry;
        this.entropy = entropy;
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
                var liveActions = RoundActions.live(round);
                publishTracedInfluence(round, gameId, eraNumber, roundNumber);
                // Must run before reconcileObscureState, while the Obscure flag still covers this round.
                publishInterceptedHands(round, liveActions, gameId, eraNumber, roundNumber);
                reconcileJamState(liveActions, gameId, eraNumber, roundNumber);
                reconcileObscureState(liveActions, gameId, roundNumber);

                if (roundNumber == SIGNATURE_REVEAL_ROUND_NUMBER) {
                    publishExposeSignatures(gameId, eraNumber, liveActions);
                }
                if (roundNumber == FINAL_ROUND_NUMBER) {
                    publishExposeBehaviorChanges(gameId, eraNumber, round);
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
            summaries.add(
                    new ActionSummary(action.playerId(), action.publicCategory().orElse(null), action.family(), false));
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
        // lifecycle; Intercept at that round's close reads this flag to reveal a decoy hand instead of the
        // real one. Applied at round close (not at submit) so simultaneous
        // submissions in the closing round cannot observe a mid-round flag change. No separate lock:
        // reconcileJamState runs immediately before in the same transaction and already holds all rows.
        var obscuredPlayerIds =
                roundNumber < FINAL_ROUND_NUMBER ? RoundActions.obscureSubmitters(liveActions) : Set.<UUID>of();

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

    private void publishTracedInfluence(ActionRound round, UUID gameId, int eraNumber, int roundNumber) {
        for (var result : traceResolver.resolve(round)) {
            actionEventPublisher.publish(DomainEventEnvelope.create(
                    round.id(),
                    ActionRound.AGGREGATE_TYPE,
                    gameId,
                    DomainEventEnvelope.SCHEMA_VERSION_V1,
                    new InfluenceTraced(
                            gameId,
                            eraNumber,
                            roundNumber,
                            result.tracerPlayerId(),
                            result.targetEventId(),
                            result.influencerPlayerIds(),
                            result.mimicInfluencerPlayerIds()),
                    clock));
        }
    }

    private void publishInterceptedHands(
            ActionRound round, List<SubmittedAction> liveActions, UUID gameId, int eraNumber, int roundNumber) {
        var intercepts = liveActions.stream()
                .filter(SubmittedAction.CardAction.class::isInstance)
                .map(SubmittedAction.CardAction.class::cast)
                .filter(card -> card.cardType() == CardType.INTERCEPT)
                .toList();
        if (intercepts.isEmpty()) {
            return;
        }
        playerStateRepository.lockAllByGameId(gameId);
        var statesByPlayer = new HashMap<UUID, PlayerState>();
        for (var playerState : playerStateRepository.findAllByGameId(gameId)) {
            statesByPlayer.put(playerState.playerId(), playerState);
        }
        // One decoy hand per obscured target, shared by every Intercept resolving now, so comparing
        // two reveals cannot tell it apart from a real hand.
        var decoyHands = new HashMap<UUID, List<PlayerState.CardInstance>>();
        var revealedTargets = new LinkedHashSet<PlayerState>();
        var roundCoordinate = EntropyCoordinate.none().era(eraNumber).round(roundNumber);
        for (var intercept : intercepts) {
            var target = java.util.Optional.ofNullable(statesByPlayer.get(intercept.targetPlayerId()));
            var revealRandom = entropy.generator(
                    EntropyPurpose.INTERCEPT_REVEAL,
                    roundCoordinate.player(intercept.playerId()).subject(intercept.targetPlayerId()));
            var sample = target.map(state -> InterceptHandSampler.select(
                            state.isObscured()
                                    ? decoyHands.computeIfAbsent(
                                            state.playerId(), ignored -> decoyHand(state, roundCoordinate))
                                    : state.hand(),
                            intercept.grade(),
                            revealRandom))
                    .orElseGet(List::of);
            target.filter(state -> !state.isObscured()).ifPresent(state -> {
                state.markRevealed(sample);
                revealedTargets.add(state);
            });
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
        revealedTargets.forEach(playerStateRepository::save);
    }

    private List<PlayerState.CardInstance> decoyHand(PlayerState target, EntropyCoordinate roundCoordinate) {
        var targetCoordinate = roundCoordinate.player(target.playerId());
        return InterceptHandSampler.decoyHand(
                target,
                cardDrawWeights(),
                entropy.generator(EntropyPurpose.INTERCEPT_DECOY, targetCoordinate),
                slot -> entropy.identity(IdentityKind.DECOY_CARD_INSTANCE, targetCoordinate.slot(slot)));
    }

    private CardDrawWeights cardDrawWeights() {
        return new CardDrawWeights(gameRules.cardCategoryWeights(), gameRules.cardGradeWeights());
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

    private void publishExposeSignatures(UUID gameId, int eraNumber, List<SubmittedAction> liveActions) {
        var liveExposePlayerIds = liveActions.stream()
                .filter(SubmittedAction.SpecialActionSubmission.class::isInstance)
                .map(SubmittedAction.SpecialActionSubmission.class::cast)
                .filter(special ->
                        special.specialAction() == io.github.temporalrift.game.shared.domain.model.SpecialAction.EXPOSE)
                .map(SubmittedAction.SpecialActionSubmission::playerId)
                .collect(java.util.stream.Collectors.toSet());
        var exposedStates = activistEraStateRepository.findExposedByGameIdAndEraNumber(gameId, eraNumber).stream()
                .filter(state -> liveExposePlayerIds.contains(state.activistPlayerId()))
                .filter(state -> state.exposedSignature() != null)
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
    }

    private void publishExposeBehaviorChanges(UUID gameId, int eraNumber, ActionRound round3) {
        var exposedStates = activistEraStateRepository.findExposedByGameIdAndEraNumber(gameId, eraNumber);
        if (exposedStates.isEmpty()) {
            return;
        }
        var cancelledExposePlayerIds = actionRoundRepository
                .findByGameIdAndEraNumberAndRoundNumber(gameId, eraNumber, SIGNATURE_REVEAL_ROUND_NUMBER)
                .map(ActionRound::submittedActions)
                .map(RoundCancellation::cancelledPlayerIds)
                .orElseGet(Set::of);
        exposedStates.stream()
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
                        actionEventPublisher.publish(envelope);
                    }
                });
    }

    // In-process only, published directly (not through ActionRoundEventPublication): built from the
    // final round's own submittedActions, which is complete and final by the time close() returns, so
    // it cannot race the independently-dispatched per-submission ForesightDeclared listener the way
    // onActionRoundClosed alone would.
    private void publishFinalRoundActionFacts(UUID gameId, int eraNumber, ActionRound round) {
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
        var mimicFacts = new ArrayList<EraActionFactsFinalized.RevisionistFact>();
        var latestRewriteFacts = new LinkedHashMap<UUID, EraActionFactsFinalized.RevisionistFact>();
        var fulfillmentFacts = new java.util.LinkedHashSet<EraActionFactsFinalized.FulfillmentFact>();
        var exposeFacts = activistEraStateRepository.findExposedByGameIdAndEraNumber(gameId, eraNumber).stream()
                .filter(state -> !cancelledRoundTwoPlayerIds.contains(state.activistPlayerId()))
                .filter(state -> state.exposedSignature() != null)
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
                exposeFacts,
                activistDeclarationFacts,
                java.util.stream.Stream.concat(latestRewriteFacts.values().stream(), mimicFacts.stream())
                        .toList(),
                List.copyOf(fulfillmentFacts),
                corruptCorrelationFacts,
                tracedMimicPlayerIds(eraRounds)));
    }

    // Revisionists another player's Trace reported as a Mimic influencer at any of this era's round closes.
    private List<UUID> tracedMimicPlayerIds(List<ActionRound> eraRounds) {
        var traced = new LinkedHashSet<UUID>();
        eraRounds.stream()
                .flatMap(eraRound -> traceResolver.resolve(eraRound).stream())
                .forEach(result -> result.mimicInfluencerPlayerIds().stream()
                        .filter(playerId -> !playerId.equals(result.tracerPlayerId()))
                        .forEach(traced::add));
        return List.copyOf(traced);
    }

    private static java.util.stream.Stream<SubmittedAction> uncancelledActions(ActionRound round) {
        return RoundActions.live(round).stream();
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
