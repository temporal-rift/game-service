package io.github.temporalrift.game.action.application.saga;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Coordinates the lifecycle of one action round.
 *
 * <p>This is the point where the timer-expiry path and the player-submission path are merged so the
 * round closes exactly once.
 */
interface ActionRoundSaga {

    StartResult start(UUID gameId, int eraNumber, int roundNumber, List<UUID> playerIds);

    void handlePlayerSubmitted(UUID gameId, int eraNumber, int roundNumber, UUID playerId);

    /**
     * Metadata needed by the timer scheduler after the saga start transaction commits.
     */
    record StartResult(UUID sagaId, Instant timerExpiresAt) {}
}
