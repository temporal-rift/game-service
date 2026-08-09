package io.github.temporalrift.game.action.application.port.in;

import java.util.Objects;
import java.util.UUID;

/** Accepts one eligible card from a player during an open paradox-resolution phase. */
public interface PlayParadoxResolutionCardUseCase {

    /** Validates, consumes, and publishes a player's single resolution-phase card. */
    Result handle(Command command);

    /** Input required by the era-scoped paradox-resolution submission boundary. */
    record Command(
            UUID gameId, int eraNumber, UUID playerId, UUID cardInstanceId, UUID targetEventId, UUID targetOutcomeId) {

        public Command {
            Objects.requireNonNull(gameId, "gameId must not be null");
            Objects.requireNonNull(playerId, "playerId must not be null");
            Objects.requireNonNull(cardInstanceId, "cardInstanceId must not be null");
            Objects.requireNonNull(targetEventId, "targetEventId must not be null");
            Objects.requireNonNull(targetOutcomeId, "targetOutcomeId must not be null");
        }
    }

    /** Successful submission coordinates returned to the REST adapter. */
    record Result(UUID gameId, int eraNumber, UUID playerId) {}
}
