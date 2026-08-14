package io.github.temporalrift.game.action.domain.handselection;

import java.util.UUID;

public class HandSelectionNotOpenException extends RuntimeException {
    public HandSelectionNotOpenException(UUID gameId, int eraNumber, UUID playerId) {
        super("Hand selection is not open for game " + gameId + ", era " + eraNumber + ", player " + playerId);
    }
}
