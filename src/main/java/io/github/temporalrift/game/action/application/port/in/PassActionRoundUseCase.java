package io.github.temporalrift.game.action.application.port.in;

import java.util.Objects;
import java.util.UUID;

/**
 * Accepts a player's explicit pass for an open action round.
 *
 * <p>A pass consumes the player's single submission slot exactly like a card or special, so it counts
 * toward closing the round early, but spends no card and is published only at close, as the same neutral
 * skip a timer expiry produces. As with {@link PlayCardUseCase}, {@link Result#roundClosed()} only reports
 * whether this pass emptied the pending list; {@code ActionRoundSaga} still owns the actual close.
 */
public interface PassActionRoundUseCase {

    /** Validates and stores a player's pass for a specific action round. */
    Result handle(Command command);

    /** Input required to pass an action round. */
    record Command(UUID gameId, int eraNumber, int roundNumber, UUID playerId) {

        public Command {
            Objects.requireNonNull(gameId, "gameId must not be null");
            Objects.requireNonNull(playerId, "playerId must not be null");
        }
    }

    /** Result returned after the pass is stored and its in-process event is published. */
    record Result(UUID gameId, int eraNumber, int roundNumber, UUID playerId, boolean roundClosed) {}
}
