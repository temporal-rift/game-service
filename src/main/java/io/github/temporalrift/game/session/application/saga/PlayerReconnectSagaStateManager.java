package io.github.temporalrift.game.session.application.saga;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.session.domain.port.out.PlayerReconnectSagaRepository;
import io.github.temporalrift.game.session.domain.saga.PlayerReconnectSagaState;
import io.github.temporalrift.game.session.domain.saga.PlayerReconnectSagaStatus;

@Component
class PlayerReconnectSagaStateManager {

    private final PlayerReconnectSagaRepository repository;

    PlayerReconnectSagaStateManager(PlayerReconnectSagaRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = REQUIRES_NEW)
    PlayerReconnectSagaState initGracePeriod(UUID sagaId, UUID gameId, UUID playerId, Instant graceExpiresAt) {
        return repository.save(new PlayerReconnectSagaState(
                sagaId, gameId, playerId, PlayerReconnectSagaStatus.GRACE_PERIOD, graceExpiresAt));
    }

    @Transactional(propagation = REQUIRES_NEW)
    void reconnect(UUID sagaId) {
        repository
                .findBySagaId(sagaId)
                .filter(s -> s.status() == PlayerReconnectSagaStatus.GRACE_PERIOD)
                .ifPresent(s -> repository.save(s.withStatus(PlayerReconnectSagaStatus.RECONNECTED)));
    }

    @Transactional(propagation = REQUIRES_NEW)
    void abandon(UUID sagaId) {
        repository
                .findBySagaId(sagaId)
                .filter(s -> s.status() == PlayerReconnectSagaStatus.GRACE_PERIOD)
                .ifPresent(s -> repository.save(s.withStatus(PlayerReconnectSagaStatus.ABANDONED)));
    }

    boolean hasActiveGracePeriod(UUID gameId, UUID playerId) {
        return repository
                .findByGameIdAndPlayerId(gameId, playerId)
                .map(s -> s.status() == PlayerReconnectSagaStatus.GRACE_PERIOD)
                .orElse(false);
    }

    Optional<PlayerReconnectSagaState> findBySagaId(UUID sagaId) {
        return repository.findBySagaId(sagaId);
    }

    Optional<PlayerReconnectSagaState> findByGameIdAndPlayerId(UUID gameId, UUID playerId) {
        return repository.findByGameIdAndPlayerId(gameId, playerId);
    }

    List<PlayerReconnectSagaState> findAllInGracePeriod() {
        return repository.findAllByStatus(PlayerReconnectSagaStatus.GRACE_PERIOD);
    }

    long countActiveGracePeriodForGame(UUID gameId) {
        return repository.countByGameIdAndStatus(gameId, PlayerReconnectSagaStatus.GRACE_PERIOD);
    }
}
