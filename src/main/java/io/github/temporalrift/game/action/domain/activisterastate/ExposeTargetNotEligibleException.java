package io.github.temporalrift.game.action.domain.activisterastate;

import java.util.UUID;

/** Raised when Expose targets a player without a qualifying Round-1 probability signature. */
public final class ExposeTargetNotEligibleException extends RuntimeException {

    public ExposeTargetNotEligibleException(UUID playerId) {
        super("Player " + playerId + " has no qualifying Round-1 probability-influence signature");
    }
}
