package io.github.temporalrift.game.action.domain.actionround;

import java.util.Objects;
import java.util.UUID;

/** The fixed identity and timing parameters of an {@link ActionRound}. */
public record ActionRoundConfig(UUID gameId, int eraNumber, int roundNumber, int timerSeconds) {

    public ActionRoundConfig {
        Objects.requireNonNull(gameId, "gameId must not be null");
    }
}
