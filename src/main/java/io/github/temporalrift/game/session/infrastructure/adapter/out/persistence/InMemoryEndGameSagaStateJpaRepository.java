package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryEndGameSagaStateJpaRepository extends InMemoryJpaRepository<EndGameSagaStateJpaEntity, UUID>
        implements EndGameSagaStateJpaRepository {

    InMemoryEndGameSagaStateJpaRepository(InMemoryDatabase database) {
        super(database, TableDefinition.of("end_game_saga_state", EndGameSagaStateJpaEntity::getGameId));
    }

    @Override
    public Optional<EndGameSagaStateJpaEntity> findByGameIdWithLock(UUID gameId) {
        return findByIdForUpdate(gameId);
    }

    @Override
    public int claimIfAbsent(UUID gameId, String triggerType, String status, UUID[] playerIds) {
        var saga = new EndGameSagaStateJpaEntity();
        saga.setGameId(gameId);
        saga.setTriggerType(triggerType);
        saga.setStatus(status);
        saga.setPlayerIds(List.of(playerIds));
        return insertRowIfAbsent(saga) ? 1 : 0;
    }
}
