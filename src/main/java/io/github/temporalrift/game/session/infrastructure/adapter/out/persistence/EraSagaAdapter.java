package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.session.domain.port.out.EraSagaRepository;
import io.github.temporalrift.game.session.domain.saga.EraSagaState;
import io.github.temporalrift.game.session.domain.saga.EraSagaStatus;

@Component
public class EraSagaAdapter implements EraSagaRepository {

    private final EraSagaStateJpaRepository jpaRepository;

    EraSagaAdapter(EraSagaStateJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public EraSagaState save(EraSagaState state) {
        jpaRepository.save(toEntity(state));
        return state;
    }

    @Override
    public Optional<EraSagaState> findByGameId(UUID gameId) {
        return jpaRepository.findById(gameId).map(this::toDomain);
    }

    @Override
    public Optional<EraSagaState> findByGameIdWithLock(UUID gameId) {
        return jpaRepository.findByGameIdWithLock(gameId).map(this::toDomain);
    }

    private EraSagaStateJpaEntity toEntity(EraSagaState state) {
        var entity = new EraSagaStateJpaEntity();
        entity.setGameId(state.gameId());
        entity.setEraNumber(state.eraNumber());
        entity.setStatus(state.status().name());
        entity.setPlayerIds(state.playerIds());
        return entity;
    }

    private EraSagaState toDomain(EraSagaStateJpaEntity entity) {
        return new EraSagaState(
                entity.getGameId(),
                entity.getEraNumber(),
                EraSagaStatus.valueOf(entity.getStatus()),
                entity.getPlayerIds());
    }
}
