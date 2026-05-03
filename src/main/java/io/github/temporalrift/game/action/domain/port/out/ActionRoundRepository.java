package io.github.temporalrift.game.action.domain.port.out;

import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.action.domain.actionround.ActionRound;

public interface ActionRoundRepository {

    ActionRound save(ActionRound actionRound);

    Optional<ActionRound> findById(UUID id);

    Optional<ActionRound> findByGameIdAndEraNumberAndRoundNumber(UUID gameId, int eraNumber, int roundNumber);

    Optional<ActionRound> findByIdForUpdate(UUID id);
}
