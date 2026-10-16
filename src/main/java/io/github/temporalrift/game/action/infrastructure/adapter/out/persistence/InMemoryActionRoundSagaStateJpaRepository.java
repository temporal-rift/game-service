package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;

import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaStatus;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryActionRoundSagaStateJpaRepository extends InMemoryJpaRepository<ActionRoundSagaStateJpaEntity, UUID>
        implements ActionRoundSagaStateJpaRepository {

    private static final String WAITING = ActionRoundSagaStatus.WAITING.name();

    InMemoryActionRoundSagaStateJpaRepository(InMemoryDatabase database) {
        super(
                database,
                TableDefinition.<UUID, ActionRoundSagaStateJpaEntity>of(
                                "action_round_saga_state", ActionRoundSagaStateJpaEntity::getSagaId)
                        .unique(
                                "game_era_round",
                                saga -> List.of(saga.getGameId(), saga.getEraNumber(), saga.getRoundNumber())));
    }

    @Override
    public Optional<ActionRoundSagaStateJpaEntity> findByGameIdAndEraNumberAndRoundNumber(
            UUID gameId, int eraNumber, int roundNumber) {
        return findFirst(saga -> matches(saga, gameId, eraNumber, roundNumber));
    }

    @Override
    public Optional<ActionRoundSagaStateJpaEntity> findByGameIdAndEraNumberAndRoundNumberWithLock(
            UUID gameId, int eraNumber, int roundNumber) {
        return findFirstForUpdate(saga -> matches(saga, gameId, eraNumber, roundNumber));
    }

    @Override
    public List<ActionRoundSagaStateJpaEntity> findWaitingDueBy(Instant deadline, Pageable page) {
        var due = findMatching(
                saga -> WAITING.equals(saga.getStatus())
                        && !saga.getTimerExpiresAt().isAfter(deadline),
                Comparator.comparing(ActionRoundSagaStateJpaEntity::getTimerExpiresAt));
        return page.isPaged() ? due.stream().limit(page.getPageSize()).toList() : due;
    }

    private static boolean matches(ActionRoundSagaStateJpaEntity saga, UUID gameId, int eraNumber, int roundNumber) {
        return saga.getGameId().equals(gameId)
                && saga.getEraNumber() == eraNumber
                && saga.getRoundNumber() == roundNumber;
    }
}
