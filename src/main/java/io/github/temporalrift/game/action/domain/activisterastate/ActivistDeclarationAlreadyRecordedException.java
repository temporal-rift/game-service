package io.github.temporalrift.game.action.domain.activisterastate;

import java.util.UUID;

public final class ActivistDeclarationAlreadyRecordedException extends RuntimeException {

    public ActivistDeclarationAlreadyRecordedException(UUID playerId, int eraNumber) {
        super("Activist " + playerId + " already has a declaration in era " + eraNumber);
    }
}
