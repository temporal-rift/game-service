package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.action.domain.port.out.ActionRoundSagaRepository;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaState;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaStatus;

@Component
public class ActionRoundSagaAdapter implements ActionRoundSagaRepository {

    private final ActionRoundSagaStateJpaRepository jpaRepository;

    ActionRoundSagaAdapter(ActionRoundSagaStateJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ActionRoundSagaState save(ActionRoundSagaState state) {
        jpaRepository.save(toEntity(state));
        return state;
    }

    @Override
    public Optional<ActionRoundSagaState> findBySagaId(UUID sagaId) {
        return jpaRepository.findById(sagaId).map(this::toDomain);
    }

    @Override
    public Optional<ActionRoundSagaState> findByGameIdAndEraNumberAndRoundNumber(
            UUID gameId, int eraNumber, int roundNumber) {
        return jpaRepository
                .findByGameIdAndEraNumberAndRoundNumber(gameId, eraNumber, roundNumber)
                .map(this::toDomain);
    }

    @Override
    public Optional<ActionRoundSagaState> findByGameIdAndEraNumberAndRoundNumberWithLock(
            UUID gameId, int eraNumber, int roundNumber) {
        return jpaRepository
                .findByGameIdAndEraNumberAndRoundNumberWithLock(gameId, eraNumber, roundNumber)
                .map(this::toDomain);
    }

    @Override
    public List<ActionRoundSagaState> findWaitingDueBy(Instant deadline) {
        return jpaRepository.findWaitingDueBy(deadline).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<ActionRoundSagaState> findAllClosing() {
        return jpaRepository.findAllClosing().stream().map(this::toDomain).toList();
    }

    private ActionRoundSagaStateJpaEntity toEntity(ActionRoundSagaState state) {
        var entity = new ActionRoundSagaStateJpaEntity();
        entity.setSagaId(state.sagaId());
        entity.setGameId(state.gameId());
        entity.setEraNumber(state.eraNumber());
        entity.setRoundNumber(state.roundNumber());
        entity.setStatus(state.status().name());
        entity.setPendingPlayerIds(state.pendingPlayerIds().toArray(UUID[]::new));
        entity.setTimerExpiresAt(state.timerExpiresAt());
        return entity;
    }

    private ActionRoundSagaState toDomain(ActionRoundSagaStateJpaEntity entity) {
        return new ActionRoundSagaState(
                entity.getSagaId(),
                entity.getGameId(),
                entity.getEraNumber(),
                entity.getRoundNumber(),
                ActionRoundSagaStatus.valueOf(entity.getStatus()),
                List.copyOf(Arrays.asList(entity.getPendingPlayerIds())),
                entity.getTimerExpiresAt());
    }
}
