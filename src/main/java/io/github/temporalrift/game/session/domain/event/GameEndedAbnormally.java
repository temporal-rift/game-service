package io.github.temporalrift.game.session.domain.event;

import java.util.Objects;
import java.util.UUID;

public record GameEndedAbnormally(UUID gameId, Reason reason) {

    public GameEndedAbnormally {
        Objects.requireNonNull(gameId, "gameId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
    }

    public enum Reason {
        DECK_EXHAUSTED("deck-exhausted"),
        RESOLUTION_FAILED("resolution-failed"),
        ALL_PLAYERS_ABANDONED("all-players-abandoned");

        private final String wireValue;

        Reason(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }
    }
}
