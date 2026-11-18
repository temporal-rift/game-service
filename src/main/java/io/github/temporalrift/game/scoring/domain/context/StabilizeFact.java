package io.github.temporalrift.game.scoring.domain.context;

import java.util.Objects;
import java.util.UUID;

/** A player's Stabilize on one event in an era's paradox resolution; at most one per player and event. */
public record StabilizeFact(UUID playerId, UUID eventId) {

    public StabilizeFact {
        Objects.requireNonNull(playerId, "playerId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");
    }
}
