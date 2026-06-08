package io.github.temporalrift.game.action.application.port.in;

import java.util.UUID;

import io.github.temporalrift.events.shared.SpecialAction;

/**
 * Accepts a player's faction special submission for an open action round.
 *
 * <p>Like {@code PlayCardUseCase}, the use case records the submission and publishes the aggregate
 * events, but it does not close the round directly. The action-round saga remains the single owner
 * of the close race between timer expiry and the final submission.
 */
public interface PlaySpecialActionUseCase {

    /**
     * Validates and stores a special-action submission for a player in a specific action round.
     */
    Result handle(Command command);

    /**
     * Input required to submit a faction special.
     */
    record Command(
            UUID gameId,
            int eraNumber,
            int roundNumber,
            UUID playerId,
            SpecialAction specialAction,
            UUID targetEventId,
            UUID targetOutcomeId,
            UUID targetPlayerId) {}

    /**
     * Result returned after the submission is stored and its domain events are published.
     */
    record Result(UUID gameId, int eraNumber, int roundNumber, UUID playerId, boolean roundClosed) {}
}
