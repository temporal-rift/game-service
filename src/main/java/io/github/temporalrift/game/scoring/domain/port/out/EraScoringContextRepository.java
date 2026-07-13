package io.github.temporalrift.game.scoring.domain.port.out;

import java.util.UUID;

import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.game.scoring.domain.context.EraScoringContext;

public interface EraScoringContextRepository {

    EraScoringContext getRequired(UUID gameId, int eraNumber);

    int expectedOutcomeCount(UUID gameId, int eraNumber);

    void upsertPlayerFaction(UUID gameId, UUID playerId, Faction faction);

    void upsertExpectedOutcomeCount(UUID gameId, int eraNumber, int expectedOutcomeCount);
}
