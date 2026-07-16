package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.action.domain.actionround.ActionRound;
import io.github.temporalrift.game.action.domain.actionround.RoundStatus;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;

@Component
class ActionRoundRepositoryAdapter implements ActionRoundRepository {

    private final ActionRoundJpaRepository jpaRepository;

    ActionRoundRepositoryAdapter(ActionRoundJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ActionRound save(ActionRound actionRound) {
        jpaRepository.save(toEntity(actionRound));
        return actionRound;
    }

    @Override
    public Optional<ActionRound> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<ActionRound> findByGameIdAndEraNumberAndRoundNumber(UUID gameId, int eraNumber, int roundNumber) {
        return jpaRepository
                .findByGameIdAndEraNumberAndRoundNumber(gameId, eraNumber, roundNumber)
                .map(this::toDomain);
    }

    @Override
    public Optional<ActionRound> findByGameIdAndEraNumberAndRoundNumberWithLock(
            UUID gameId, int eraNumber, int roundNumber) {
        return jpaRepository
                .findByGameIdAndEraNumberAndRoundNumberWithLock(gameId, eraNumber, roundNumber)
                .map(this::toDomain);
    }

    @Override
    public Optional<ActionRound> findByIdWithLock(UUID id) {
        return jpaRepository.findByIdWithLock(id).map(this::toDomain);
    }

    private ActionRoundJpaEntity toEntity(ActionRound round) {
        var entity = new ActionRoundJpaEntity();
        entity.setId(round.id());
        entity.setGameId(round.gameId());
        entity.setEraNumber(round.eraNumber());
        entity.setRoundNumber(round.roundNumber());
        entity.setStatus(round.status().name());
        entity.setTimerSeconds(round.timerSeconds());
        entity.setClosedReason(round.closedReason());
        entity.setPendingPlayerIds(round.pendingPlayerIds().toArray(UUID[]::new));
        entity.setSubmittedActions(round.submittedActions().stream()
                .map(StoredSubmittedAction::fromDomain)
                .toList());
        return entity;
    }

    private ActionRound toDomain(ActionRoundJpaEntity entity) {
        return ActionRound.reconstitute(
                entity.getId(),
                entity.getGameId(),
                entity.getEraNumber(),
                entity.getRoundNumber(),
                RoundStatus.valueOf(entity.getStatus()),
                entity.getTimerSeconds(),
                entity.getClosedReason(),
                new ArrayList<>(Arrays.asList(entity.getPendingPlayerIds())),
                entity.getSubmittedActions().stream()
                        .map(StoredSubmittedAction::toDomain)
                        .collect(ArrayList::new, ArrayList::add, ArrayList::addAll));
    }
}
