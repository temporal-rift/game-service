package io.github.temporalrift.game.scoring.domain.playerscore;

public class InvalidScoreEraException extends RuntimeException {

    public InvalidScoreEraException(int eraNumber) {
        super("Score era number must be positive: " + eraNumber);
    }
}
