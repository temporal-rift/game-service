package io.github.temporalrift.game.action.domain.specialactionerausage;

import java.util.UUID;

public final class SealGameBudgetExhaustedException extends RuntimeException {

    public SealGameBudgetExhaustedException(UUID playerId, int maxUsesPerGame) {
        super("Player " + playerId + " already used SEAL " + maxUsesPerGame + " times this game");
    }
}
