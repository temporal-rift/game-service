package io.github.temporalrift.game.action.domain.actionround;

import java.util.UUID;

public class FactionRequiredException extends RuntimeException {

    public FactionRequiredException(UUID playerId) {
        super("Player " + playerId + " must have an assigned faction to submit a special action");
    }
}
