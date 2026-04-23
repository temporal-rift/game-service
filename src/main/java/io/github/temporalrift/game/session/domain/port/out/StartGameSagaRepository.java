package io.github.temporalrift.game.session.domain.port.out;

import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.session.domain.saga.StartGameSagaState;

public interface StartGameSagaRepository {

    StartGameSagaState save(StartGameSagaState startGameSagaState);

    Optional<StartGameSagaState> findByGameId(UUID gameId);

    Optional<StartGameSagaState> findByGameIdWithLock(UUID gameId);
}
