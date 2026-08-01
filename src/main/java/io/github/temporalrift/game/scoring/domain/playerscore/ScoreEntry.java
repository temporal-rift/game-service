package io.github.temporalrift.game.scoring.domain.playerscore;

import java.util.Objects;

public record ScoreEntry(int eraNumber, ScoreReason reason, int pointsDelta, int newTotal) {

    public ScoreEntry {
        if (eraNumber < 0 || (eraNumber == 0 && reason != ScoreReason.FACTION_UNIDENTIFIED)) {
            throw new InvalidScoreEraException(eraNumber);
        }
        Objects.requireNonNull(reason, "reason must not be null");
    }
}
