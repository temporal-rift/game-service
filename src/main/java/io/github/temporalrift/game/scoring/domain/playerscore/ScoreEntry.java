package io.github.temporalrift.game.scoring.domain.playerscore;

import java.util.Objects;

public record ScoreEntry(int eraNumber, ScoreReason reason, int pointsDelta, int newTotal) {

    public ScoreEntry {
        if (eraNumber < 1) {
            throw new InvalidScoreEraException(eraNumber);
        }
        Objects.requireNonNull(reason, "reason must not be null");
    }
}
