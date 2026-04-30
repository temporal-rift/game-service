package io.github.temporalrift.game.session.domain.port.out;

import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.session.domain.saga.EraSagaState;

public interface EraSagaRepository {

    EraSagaState save(EraSagaState state);

    Optional<EraSagaState> findByGameId(UUID gameId);

    Optional<EraSagaState> findByGameIdWithLock(UUID gameId);
}
