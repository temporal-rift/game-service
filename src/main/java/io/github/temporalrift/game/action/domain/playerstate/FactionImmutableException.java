package io.github.temporalrift.game.action.domain.playerstate;

import java.util.UUID;

public class FactionImmutableException extends RuntimeException {

    public FactionImmutableException(UUID playerId) {
        super("Faction is immutable and cannot be reassigned for player: " + playerId);
    }
}
