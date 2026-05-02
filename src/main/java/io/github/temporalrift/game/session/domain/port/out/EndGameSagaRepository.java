package io.github.temporalrift.game.session.domain.port.out;

import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.session.domain.saga.EndGameSagaState;

public interface EndGameSagaRepository {

    EndGameSagaState save(EndGameSagaState state);

    Optional<EndGameSagaState> findByGameId(UUID gameId);

    Optional<EndGameSagaState> findByGameIdWithLock(UUID gameId);
}
