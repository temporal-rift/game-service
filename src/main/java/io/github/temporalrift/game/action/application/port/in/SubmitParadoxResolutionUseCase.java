package io.github.temporalrift.game.action.application.port.in;

import java.util.Objects;
import java.util.UUID;

/** Accepts a player's single submission during an open paradox-resolution phase: one eligible card, or a pass. */
public interface SubmitParadoxResolutionUseCase {

    /** Validates and records the submission, consuming the player's single phase slot. */
    Result handle(Command command);

    /** One submission for the era's paradox-resolution phase. */
    sealed interface Command permits PlayCard, Pass {

        UUID gameId();

        int eraNumber();

        UUID playerId();
    }

    /** Plays one eligible resolution card against an event outcome. */
    record PlayCard(
            UUID gameId, int eraNumber, UUID playerId, UUID cardInstanceId, UUID targetEventId, UUID targetOutcomeId)
            implements Command {

        public PlayCard {
            Objects.requireNonNull(gameId, "gameId must not be null");
            Objects.requireNonNull(playerId, "playerId must not be null");
            Objects.requireNonNull(cardInstanceId, "cardInstanceId must not be null");
            Objects.requireNonNull(targetEventId, "targetEventId must not be null");
            Objects.requireNonNull(targetOutcomeId, "targetOutcomeId must not be null");
        }
    }

    /** Passes the phase without a card. */
    record Pass(UUID gameId, int eraNumber, UUID playerId) implements Command {

        public Pass {
            Objects.requireNonNull(gameId, "gameId must not be null");
            Objects.requireNonNull(playerId, "playerId must not be null");
        }
    }

    /** Successful submission coordinates returned to the REST adapter. */
    record Result(UUID gameId, int eraNumber, UUID playerId) {}
}
