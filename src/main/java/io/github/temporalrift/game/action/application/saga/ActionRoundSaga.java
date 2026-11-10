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

    /**
     * Opens the round for {@code playerIds}; {@code activePlayerIds} (those who have not abandoned) are the
     * players told which of their specials are available in it.
     */
    StartResult start(UUID gameId, int eraNumber, int roundNumber, List<UUID> playerIds, List<UUID> activePlayerIds);

    void handlePlayerSubmitted(UUID gameId, int eraNumber, int roundNumber, UUID playerId);

    /**
     * Metadata needed by the timer scheduler after the saga start transaction commits.
     */
    record StartResult(UUID sagaId, Instant timerExpiresAt) {}
}
