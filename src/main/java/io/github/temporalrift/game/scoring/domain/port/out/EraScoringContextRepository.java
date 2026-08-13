package io.github.temporalrift.game.scoring.domain.port.out;

import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.scoring.domain.context.EraScoringContext;
import io.github.temporalrift.game.scoring.domain.context.PendingEraScoringCompletion;
import io.github.temporalrift.game.scoring.domain.event.EraResolutionCompleted;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.shared.ActivistDeclarationRecorded;
import io.github.temporalrift.game.shared.ActivistDeclarationResolved;
import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.shared.SpecialAction;

public interface EraScoringContextRepository {

    EraScoringContext getRequired(UUID gameId, int eraNumber);

    int expectedOutcomeCount(UUID gameId, int eraNumber);

    void upsertPlayerFaction(UUID gameId, UUID playerId, Faction faction);

    void upsertExpectedOutcomeCount(UUID gameId, int eraNumber, int expectedOutcomeCount);

    void recordChainFact(UUID gameId, UUID playerId, UUID chainId, ScoreReason reason, int eraNumber);

    void recordParadoxCascadeFact(
            UUID gameId, int eraNumber, UUID paradoxId, UUID affectedEventId, List<UUID> detonatedByPlayerIds);

    void upsertEventOutcomeBaseline(UUID gameId, int eraNumber, UUID eventId, int startingOutcomeCount);

    void upsertWrittenOutcome(UUID gameId, int eraNumber, UUID eventId, UUID outcomeId, UUID playerId);

    void recordAnnihilatedOutcome(UUID gameId, int eraNumber, UUID eventId, UUID outcomeId, UUID playerId);

    void recordActionFact(UUID gameId, int eraNumber, UUID playerId, Faction faction, ScoreReason reason);

    void upsertActivistDeclaration(ActivistDeclarationRecorded declaration);

    void saveEraResolutionCompleted(EraResolutionCompleted resolution);

    boolean eraResolutionCompleted(UUID gameId, int eraNumber);

    List<PendingEraScoringCompletion> findResolvedErasNotYetScored();

    int requiredAppliedOutcomeCount(UUID gameId, int eraNumber);

    java.util.List<ActivistDeclarationResolved> resolveActivistDeclarations(UUID gameId, int eraNumber);

    boolean activistDeclarationsResolved(UUID gameId, int eraNumber);

    boolean actionFactsReady(UUID gameId, int eraNumber);

    void markActionFactsReady(UUID gameId, int eraNumber);

    void recordRevisionistAction(
            UUID gameId, int eraNumber, UUID playerId, SpecialAction action, UUID targetEventId, UUID targetOutcomeId);

    void resolveRevisionistActions(UUID gameId, int eraNumber);

    boolean revisionistActionsResolved(UUID gameId, int eraNumber);

    void recordFulfillmentDeclaration(UUID gameId, int eraNumber, UUID playerId, UUID targetEventId);

    /**
     * Persists a candidate Corrupt fact with {@code tookEffect} unset — see
     * {@code scoring_context_corrupt_correlation}'s migration and {@code EraScoreEvaluator.eraserDecisions}.
     * Does not by itself drive a score credit until {@link #confirmCorruptInversion} is called.
     */
    void recordCorruptCorrelation(
            UUID gameId,
            int eraNumber,
            UUID corruptingPlayerId,
            UUID targetPlayerId,
            UUID cardInstanceId,
            UUID targetEventId,
            UUID sourceOutcomeId,
            UUID targetOutcomeId);

    /**
     * Confirms whether a previously recorded Corrupt correlation's inversion actually took effect (a
     * Seal can void it), unlocking {@code CORRUPTED_OPPONENT_CARD} for the corrupting player. Not yet
     * called by any listener: the timeline-service resolution-time fact this needs does not exist yet
     * (temporal-rift/timeline-service#12 / #16). Ready for that future consumer.
     */
    void confirmCorruptInversion(
            UUID gameId, int eraNumber, UUID corruptingPlayerId, UUID cardInstanceId, boolean tookEffect);
}
