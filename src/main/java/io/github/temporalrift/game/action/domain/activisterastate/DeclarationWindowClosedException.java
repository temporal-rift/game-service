package io.github.temporalrift.game.action.domain.activisterastate;

import java.util.UUID;

public final class DeclarationWindowClosedException extends RuntimeException {

    public DeclarationWindowClosedException(UUID gameId, int eraNumber) {
        super("The declaration window is closed for game " + gameId + " era " + eraNumber);
    }
}
