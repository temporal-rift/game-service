package io.github.temporalrift.game.session.domain.port.out;

import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.session.domain.saga.EndGameSagaState;

public interface EndGameSagaRepository {

    EndGameSagaState save(EndGameSagaState state);

    /** Atomically inserts the RUNNING claim; returns false if a row for this gameId already exists. */
    boolean claimIfAbsent(EndGameSagaState state);

    Optional<EndGameSagaState> findByGameIdWithLock(UUID gameId);
}
