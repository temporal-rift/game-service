package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.session.domain.port.out.PlayerReconnectSagaRepository;
import io.github.temporalrift.game.session.domain.saga.PlayerReconnectSagaState;
import io.github.temporalrift.game.session.domain.saga.PlayerReconnectSagaStatus;

@Component
public class PlayerReconnectSagaAdapter implements PlayerReconnectSagaRepository {

    private final PlayerReconnectSagaStateJpaRepository jpaRepository;

    PlayerReconnectSagaAdapter(PlayerReconnectSagaStateJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public PlayerReconnectSagaState save(PlayerReconnectSagaState state) {
        jpaRepository.save(toEntity(state));
        return state;
    }

    @Override
    public Optional<PlayerReconnectSagaState> findBySagaId(UUID sagaId) {
        return jpaRepository.findById(sagaId).map(this::toDomain);
    }

    @Override
    public Optional<PlayerReconnectSagaState> findByGameIdAndPlayerId(UUID gameId, UUID playerId) {
        return jpaRepository.findByGameIdAndPlayerId(gameId, playerId).map(this::toDomain);
    }

    @Override
    public List<PlayerReconnectSagaState> findAllByStatus(PlayerReconnectSagaStatus status) {
        return jpaRepository.findAllByStatus(status.name()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public long countByGameIdAndStatus(UUID gameId, PlayerReconnectSagaStatus status) {
        return jpaRepository.countByGameIdAndStatus(gameId, status.name());
    }

    private PlayerReconnectSagaStateJpaEntity toEntity(PlayerReconnectSagaState state) {
        var entity = new PlayerReconnectSagaStateJpaEntity();
        entity.setSagaId(state.sagaId());
        entity.setGameId(state.gameId());
        entity.setPlayerId(state.playerId());
        entity.setStatus(state.status().name());
        entity.setGraceExpiresAt(state.graceExpiresAt());
        return entity;
    }

    private PlayerReconnectSagaState toDomain(PlayerReconnectSagaStateJpaEntity entity) {
        return new PlayerReconnectSagaState(
                entity.getSagaId(),
                entity.getGameId(),
                entity.getPlayerId(),
                PlayerReconnectSagaStatus.valueOf(entity.getStatus()),
                entity.getGraceExpiresAt());
    }
}
