package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.UUID;

/**
 * One action fact to insert: the scoring reason a player's action produced in an era, and the event and outcome its
 * bet named (null when no bet stands behind it).
 */
record ActionFactRow(
        UUID id,
        UUID gameId,
        int eraNumber,
        UUID playerId,
        String faction,
        String reason,
        UUID targetEventId,
        UUID targetOutcomeId) {}
