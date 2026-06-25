package io.github.temporalrift.game.scoring.domain.port.out;

import java.util.UUID;

import io.github.temporalrift.game.scoring.domain.context.EraScoringContext;

public interface EraScoringContextRepository {

    EraScoringContext getRequired(UUID gameId, int eraNumber);
}
