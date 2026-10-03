package io.github.temporalrift.game.action.domain.declarationphase;

import java.util.UUID;

public final class DeclarationAlreadyDecidedException extends RuntimeException {
    public DeclarationAlreadyDecidedException(UUID playerId, int eraNumber) {
        super("Player " + playerId + " already decided the declaration opportunity in era " + eraNumber);
    }
}
