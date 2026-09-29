package io.github.temporalrift.game.action.application.port.in;

import java.util.Objects;
import java.util.UUID;

/** Accepts a player's explicit pass during an open paradox-resolution phase. */
public interface PassParadoxResolutionUseCase {

    /** Validates and records a player's pass, consuming their single phase slot without a card. */
    Result handle(Command command);

    /** Input required by the era-scoped paradox-resolution pass boundary. */
    record Command(UUID gameId, int eraNumber, UUID playerId) {

        public Command {
            Objects.requireNonNull(gameId, "gameId must not be null");
            Objects.requireNonNull(playerId, "playerId must not be null");
        }
    }

    /** Successful pass coordinates returned to the REST adapter. */
    record Result(UUID gameId, int eraNumber, UUID playerId) {}
}
