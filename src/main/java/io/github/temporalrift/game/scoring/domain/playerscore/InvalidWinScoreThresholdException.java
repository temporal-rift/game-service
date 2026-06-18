package io.github.temporalrift.game.scoring.domain.playerscore;

public class InvalidWinScoreThresholdException extends RuntimeException {

    public InvalidWinScoreThresholdException(int winScoreThreshold) {
        super("Win score threshold must be positive: " + winScoreThreshold);
    }
}
