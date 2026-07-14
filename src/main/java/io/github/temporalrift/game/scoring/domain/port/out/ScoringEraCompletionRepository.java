package io.github.temporalrift.game.scoring.domain.port.out;

import java.util.UUID;

public interface ScoringEraCompletionRepository {

    boolean tryMarkScoringComplete(UUID gameId, int eraNumber);
}
