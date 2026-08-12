package io.github.temporalrift.game.scoring.domain.context;

import java.util.Objects;
import java.util.UUID;

/** An era whose resolution barrier landed but whose scoring has not yet durably completed. */
public record PendingEraScoringCompletion(UUID gameId, int eraNumber) {

    public PendingEraScoringCompletion {
        Objects.requireNonNull(gameId, "gameId must not be null");
    }
}
