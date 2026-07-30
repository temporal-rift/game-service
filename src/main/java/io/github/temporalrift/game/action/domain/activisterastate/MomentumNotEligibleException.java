package io.github.temporalrift.game.action.domain.activisterastate;

import java.util.UUID;

public final class MomentumNotEligibleException extends RuntimeException {

    public MomentumNotEligibleException(UUID playerId, int eraNumber) {
        super("Activist " + playerId + " is not eligible to use Momentum in era " + eraNumber);
    }
}
