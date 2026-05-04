package io.github.temporalrift.game.action.domain.actionround;

import java.util.UUID;

public class RoundNotFoundException extends RuntimeException {

    public RoundNotFoundException(UUID gameId, int eraNumber, int roundNumber) {
        super("No action round found for game " + gameId + " era " + eraNumber + " round " + roundNumber);
    }
}
