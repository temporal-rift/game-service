package io.github.temporalrift.game.session.domain.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.session.domain.saga.PlayerReconnectSagaState;
import io.github.temporalrift.game.session.domain.saga.PlayerReconnectSagaStatus;

public interface PlayerReconnectSagaRepository {

    PlayerReconnectSagaState save(PlayerReconnectSagaState state);

    Optional<PlayerReconnectSagaState> findBySagaId(UUID sagaId);

    Optional<PlayerReconnectSagaState> findByGameIdAndPlayerId(UUID gameId, UUID playerId);

    List<PlayerReconnectSagaState> findByStatusDueBy(PlayerReconnectSagaStatus status, Instant deadline);

    /**
     * Atomic status transition: succeeds only when the row is still in {@code expected}. Callers
     * gate their side effects on the returned claim so a duplicate trigger (in-memory timer vs
     * sweep, or concurrent instances) runs the transition's consequences exactly once.
     */
    boolean compareAndSetStatus(UUID sagaId, PlayerReconnectSagaStatus expected, PlayerReconnectSagaStatus next);

    long countByGameIdAndStatus(UUID gameId, PlayerReconnectSagaStatus status);
}
