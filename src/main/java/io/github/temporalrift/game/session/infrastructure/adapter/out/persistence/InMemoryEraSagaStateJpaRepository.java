package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryEraSagaStateJpaRepository extends InMemoryJpaRepository<EraSagaStateJpaEntity, UUID>
        implements EraSagaStateJpaRepository {

    InMemoryEraSagaStateJpaRepository(InMemoryDatabase database) {
        super(database, TableDefinition.of("era_saga_state", EraSagaStateJpaEntity::getGameId));
    }

    @Override
    public Optional<EraSagaStateJpaEntity> findByGameIdWithLock(UUID gameId) {
        return findByIdForUpdate(gameId);
    }
}
