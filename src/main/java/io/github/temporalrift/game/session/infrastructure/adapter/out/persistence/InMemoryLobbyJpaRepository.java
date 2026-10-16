package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryLobbyJpaRepository extends InMemoryJpaRepository<LobbyJpaEntity, UUID> implements LobbyJpaRepository {

    InMemoryLobbyJpaRepository(InMemoryDatabase database) {
        super(
                database,
                TableDefinition.<UUID, LobbyJpaEntity>of("lobby", LobbyJpaEntity::getId)
                        .unique("lobby_join_code_idx", LobbyJpaEntity::getJoinCode));
    }

    @Override
    public Optional<LobbyJpaEntity> findByIdWithLock(UUID id) {
        return findByIdForUpdate(id);
    }

    @Override
    public Optional<LobbyJpaEntity> findByJoinCode(String joinCode) {
        return findFirst(lobby -> lobby.getJoinCode().equals(joinCode));
    }
}
