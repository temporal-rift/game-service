package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryStartGameSagaStateJpaRepository extends InMemoryJpaRepository<StartGameSagaStateJpaEntity, UUID>
        implements StartGameSagaStateJpaRepository {

    InMemoryStartGameSagaStateJpaRepository(InMemoryDatabase database) {
        super(
                database,
                TableDefinition.<UUID, StartGameSagaStateJpaEntity>of(
                                "game_start_saga_state", StartGameSagaStateJpaEntity::getId)
                        .unique("game_id", StartGameSagaStateJpaEntity::getGameId));
    }

    @Override
    public Optional<StartGameSagaStateJpaEntity> findByGameId(UUID gameId) {
        return findFirst(saga -> saga.getGameId().equals(gameId));
    }

    @Override
    public Optional<StartGameSagaStateJpaEntity> findByGameIdWithLock(UUID gameId) {
        return findFirstForUpdate(saga -> saga.getGameId().equals(gameId));
    }
}
