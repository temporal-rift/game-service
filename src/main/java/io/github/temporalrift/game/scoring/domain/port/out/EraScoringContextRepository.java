package io.github.temporalrift.game.scoring.domain.port.out;

import java.util.UUID;

import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.scoring.domain.context.EraScoringContext;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;

public interface EraScoringContextRepository {

    EraScoringContext getRequired(UUID gameId, int eraNumber);

    int expectedOutcomeCount(UUID gameId, int eraNumber);

    void upsertPlayerFaction(UUID gameId, UUID playerId, Faction faction);

    void upsertExpectedOutcomeCount(UUID gameId, int eraNumber, int expectedOutcomeCount);

    void recordChainFact(UUID gameId, UUID playerId, UUID chainId, ScoreReason reason, int eraNumber);
}
