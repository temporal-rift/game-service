package io.github.temporalrift.game.scoring.domain.port.out;

import java.util.UUID;

import io.github.temporalrift.game.scoring.domain.context.EraScoringContext;
import io.github.temporalrift.game.scoring.domain.event.EraResolutionCompleted;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.shared.ActivistDeclarationRecorded;
import io.github.temporalrift.game.shared.ActivistDeclarationResolved;
import io.github.temporalrift.game.shared.Faction;

public interface EraScoringContextRepository {

    EraScoringContext getRequired(UUID gameId, int eraNumber);

    int expectedOutcomeCount(UUID gameId, int eraNumber);

    void upsertPlayerFaction(UUID gameId, UUID playerId, Faction faction);

    void upsertExpectedOutcomeCount(UUID gameId, int eraNumber, int expectedOutcomeCount);

    void recordChainFact(UUID gameId, UUID playerId, UUID chainId, ScoreReason reason, int eraNumber);

    void upsertEventOutcomeBaseline(UUID gameId, int eraNumber, UUID eventId, int startingOutcomeCount);

    void upsertWrittenOutcome(UUID gameId, int eraNumber, UUID eventId, UUID outcomeId, UUID playerId);

    void recordAnnihilatedOutcome(UUID gameId, int eraNumber, UUID eventId, UUID outcomeId, UUID playerId);

    void recordActionFact(UUID gameId, int eraNumber, UUID playerId, Faction faction, ScoreReason reason);

    void upsertActivistDeclaration(ActivistDeclarationRecorded declaration);

    void saveEraResolutionCompleted(EraResolutionCompleted resolution);

    boolean eraResolutionCompleted(UUID gameId, int eraNumber);

    int requiredAppliedOutcomeCount(UUID gameId, int eraNumber);

    java.util.List<ActivistDeclarationResolved> resolveActivistDeclarations(UUID gameId, int eraNumber);

    boolean activistDeclarationsResolved(UUID gameId, int eraNumber);

    boolean actionFactsReady(UUID gameId, int eraNumber);

    void markActionFactsReady(UUID gameId, int eraNumber);
}
