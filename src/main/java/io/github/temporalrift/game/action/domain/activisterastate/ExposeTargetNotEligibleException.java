package io.github.temporalrift.game.action.domain.activisterastate;

import java.util.UUID;

/** Raised when Expose targets a player without a publicly eligible Round-1 action. */
public final class ExposeTargetNotEligibleException extends RuntimeException {

    public ExposeTargetNotEligibleException(UUID playerId) {
        super("Player " + playerId + " did not publicly play a Probability Shifter card in Round 1");
    }
}
