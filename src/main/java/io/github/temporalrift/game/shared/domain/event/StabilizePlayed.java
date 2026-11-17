package io.github.temporalrift.game.shared.domain.event;

import java.util.Objects;
import java.util.UUID;

/**
 * A player's accepted {@code STABILIZE} in an era's paradox resolution, naming the event it targets. Scoring charges
 * its cost once the era's resolution shows whether that event cascaded.
 */
public record StabilizePlayed(UUID gameId, int eraNumber, UUID playerId, UUID targetEventId) {

    public StabilizePlayed {
        Objects.requireNonNull(gameId, "gameId must not be null");
        Objects.requireNonNull(playerId, "playerId must not be null");
        Objects.requireNonNull(targetEventId, "targetEventId must not be null");
    }
}
