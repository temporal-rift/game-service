package io.github.temporalrift.game.session.domain.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.session.domain.saga.PlayerReconnectSagaState;
import io.github.temporalrift.game.session.domain.saga.PlayerReconnectSagaStatus;

public interface PlayerReconnectSagaRepository {

    PlayerReconnectSagaState save(PlayerReconnectSagaState state);

    Optional<PlayerReconnectSagaState> findBySagaId(UUID sagaId);

    Optional<PlayerReconnectSagaState> findByGameIdAndPlayerId(UUID gameId, UUID playerId);

    List<PlayerReconnectSagaState> findAllByStatus(PlayerReconnectSagaStatus status);

    long countByGameIdAndStatus(UUID gameId, PlayerReconnectSagaStatus status);
}
