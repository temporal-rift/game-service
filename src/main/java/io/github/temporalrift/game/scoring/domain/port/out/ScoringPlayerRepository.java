package io.github.temporalrift.game.scoring.domain.port.out;

import java.util.UUID;

/**
 * Scoring-owned projection of player display names, fed by public session events so scoring never
 * reads session module internals. Backs the {@code playerName} field of the score API.
 */
public interface ScoringPlayerRepository {

    void upsertPlayerName(UUID gameId, UUID playerId, String playerName);
}
