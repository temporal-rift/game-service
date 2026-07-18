package io.github.temporalrift.game.scoring.application.command;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import io.github.temporalrift.game.scoring.domain.event.OutcomeApplied;
import io.github.temporalrift.game.scoring.domain.playerscore.InvalidScoreEraException;

public record UpdateEraScoresCommand(UUID gameId, int eraNumber, List<OutcomeApplied> outcomes) {

    public UpdateEraScoresCommand {
        Objects.requireNonNull(gameId, "gameId must not be null");
        if (eraNumber < 1) {
            throw new InvalidScoreEraException(eraNumber);
        }
        Objects.requireNonNull(outcomes, "outcomes must not be null");
        outcomes = List.copyOf(outcomes);
    }
}
