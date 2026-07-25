package io.github.temporalrift.game.scoring.domain.playerscore;

import java.util.UUID;

public class ScoringGameNotFoundException extends RuntimeException {

    public ScoringGameNotFoundException(UUID gameId) {
        super("Scoring state not found for game " + gameId);
    }
}
