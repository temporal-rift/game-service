package io.github.temporalrift.game.action.domain.specialactionerausage;

import java.util.UUID;

import io.github.temporalrift.game.shared.SpecialAction;

/** Raised when a player attempts to use a once-per-era-budgeted special more than once in the same era. */
public final class SpecialActionEraBudgetExhaustedException extends RuntimeException {

    public SpecialActionEraBudgetExhaustedException(UUID playerId, SpecialAction specialAction, int eraNumber) {
        super("Player " + playerId + " already used " + specialAction + " in era " + eraNumber);
    }
}
