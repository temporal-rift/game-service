package io.github.temporalrift.game.action.domain.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaState;

public interface ActionRoundSagaRepository {

    ActionRoundSagaState save(ActionRoundSagaState state);

    Optional<ActionRoundSagaState> findBySagaId(UUID sagaId);

    Optional<ActionRoundSagaState> findByGameIdAndEraNumberAndRoundNumber(UUID gameId, int eraNumber, int roundNumber);

    Optional<ActionRoundSagaState> findByGameIdAndEraNumberAndRoundNumberWithLock(
            UUID gameId, int eraNumber, int roundNumber);

    List<ActionRoundSagaState> findAllWaiting();

    List<ActionRoundSagaState> findAllClosing();
}
