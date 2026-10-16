package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryGameJpaRepository extends InMemoryJpaRepository<GameJpaEntity, UUID> implements GameJpaRepository {

    InMemoryGameJpaRepository(InMemoryDatabase database) {
        super(database, TableDefinition.of("game", GameJpaEntity::getId));
    }

    @Override
    public Optional<GameJpaEntity> findByIdWithLock(UUID id) {
        return findByIdForUpdate(id);
    }
}
