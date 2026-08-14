package io.github.temporalrift.game.action.domain.handselection;

import java.util.UUID;

public class HandSelectionAlreadyResolvedException extends RuntimeException {
    public HandSelectionAlreadyResolvedException(UUID gameId, int eraNumber, UUID playerId) {
        super("Hand selection is already resolved for game " + gameId + ", era " + eraNumber + ", player " + playerId);
    }
}
