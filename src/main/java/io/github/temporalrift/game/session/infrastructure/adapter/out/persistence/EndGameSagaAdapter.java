package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.session.domain.port.out.EndGameSagaRepository;
import io.github.temporalrift.game.session.domain.saga.EndGameSagaState;
import io.github.temporalrift.game.session.domain.saga.EndGameSagaStatus;
import io.github.temporalrift.game.session.domain.saga.EndGameTrigger;

@Component
class EndGameSagaAdapter implements EndGameSagaRepository {

    private final EndGameSagaStateJpaRepository jpaRepository;

    EndGameSagaAdapter(EndGameSagaStateJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public EndGameSagaState save(EndGameSagaState state) {
        jpaRepository.save(toEntity(state));
        return state;
    }

    @Override
    public Optional<EndGameSagaState> findByGameId(UUID gameId) {
        return jpaRepository.findById(gameId).map(this::toDomain);
    }

    @Override
    public Optional<EndGameSagaState> findByGameIdWithLock(UUID gameId) {
        return jpaRepository.findByGameIdWithLock(gameId).map(this::toDomain);
    }

    private EndGameSagaStateJpaEntity toEntity(EndGameSagaState state) {
        var entity = new EndGameSagaStateJpaEntity();
        entity.setGameId(state.gameId());
        entity.setTriggerType(state.triggerType().name());
        entity.setStatus(state.status().name());
        entity.setPlayerIds(state.playerIds());
        return entity;
    }

    private EndGameSagaState toDomain(EndGameSagaStateJpaEntity entity) {
        return new EndGameSagaState(
                entity.getGameId(),
                EndGameTrigger.valueOf(entity.getTriggerType()),
                EndGameSagaStatus.valueOf(entity.getStatus()),
                entity.getPlayerIds());
    }
}
