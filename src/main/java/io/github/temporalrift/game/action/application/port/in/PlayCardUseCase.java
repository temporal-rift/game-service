package io.github.temporalrift.game.action.application.port.in;

import java.util.UUID;

/**
 * Accepts a player's card submission for an open action round.
 *
 * <p>The returned {@link Result#roundClosed()} flag only reports whether this submission emptied the
 * in-memory pending list. The actual round close is still owned by {@code ActionRoundSaga} so the
 * timer path and the last-submission path converge through the same locking logic.
 */
public interface PlayCardUseCase {

    /**
     * Validates and stores a card submission for a player in a specific action round.
     */
    Result handle(Command command);

    /**
     * Input required to submit a card action.
     */
    record Command(
            UUID gameId,
            int eraNumber,
            int roundNumber,
            UUID playerId,
            UUID cardInstanceId,
            UUID targetEventId,
            UUID sourceOutcomeId,
            UUID targetOutcomeId) {}

    /**
     * Result returned after the submission is stored and its domain events are published.
     */
    record Result(UUID gameId, int eraNumber, int roundNumber, UUID playerId, boolean roundClosed) {}
}
