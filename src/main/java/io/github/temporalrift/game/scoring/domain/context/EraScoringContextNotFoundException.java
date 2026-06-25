package io.github.temporalrift.game.scoring.domain.context;

import java.util.UUID;

public class EraScoringContextNotFoundException extends RuntimeException {

    public EraScoringContextNotFoundException(UUID gameId, int eraNumber) {
        super("No scoring context found for game " + gameId + " era " + eraNumber);
    }
}
