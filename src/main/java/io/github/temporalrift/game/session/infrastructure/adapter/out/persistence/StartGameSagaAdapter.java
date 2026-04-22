package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.session.domain.port.out.StartGameSagaRepository;
import io.github.temporalrift.game.session.domain.saga.StartGameSagaState;
import io.github.temporalrift.game.session.domain.saga.StartGameSagaStatus;

@Component
public class StartGameSagaAdapter implements StartGameSagaRepository {

    private final StartGameSagaStateJpaRepository jpaRepository;

    StartGameSagaAdapter(StartGameSagaStateJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public StartGameSagaState save(StartGameSagaState saga) {
        jpaRepository.save(toEntity(saga));
        return saga;
    }

    @Override
    public Optional<StartGameSagaState> findByGameId(UUID id) {
        return jpaRepository.findByGameId(id).map(this::toDomain);
    }

    private StartGameSagaStateJpaEntity toEntity(StartGameSagaState saga) {
        var entity = new StartGameSagaStateJpaEntity();
        entity.setId(saga.sagaId());
        entity.setGameId(saga.gameId());
        entity.setLobbyId(saga.lobbyId());
        entity.setStatus(saga.status().name());
        entity.setCurrentStep(saga.currentStep());
        entity.setFactionAssignments(saga.factionAssignments());
        return entity;
    }

    private StartGameSagaState toDomain(StartGameSagaStateJpaEntity entity) {
        return new StartGameSagaState(
                entity.getId(),
                entity.getGameId(),
                entity.getLobbyId(),
                StartGameSagaStatus.valueOf(entity.getStatus()),
                entity.getCurrentStep(),
                entity.getFactionAssignments());
    }
}
