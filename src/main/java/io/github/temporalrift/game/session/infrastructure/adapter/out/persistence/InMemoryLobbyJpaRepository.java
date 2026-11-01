package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryLobbyJpaRepository extends InMemoryJpaRepository<LobbyJpaEntity, UUID> implements LobbyJpaRepository {

    /** The join order the repository contract promises: ascending join time, then player id. */
    private static final Comparator<LobbyPlayerJpaEntity> JOIN_ORDER =
            Comparator.comparing(
                            LobbyPlayerJpaEntity::getJoinedAt,
                            Comparator.nullsLast(Comparator.<Instant>naturalOrder()))
                    .thenComparing(player -> player.getId().getPlayerId());

    InMemoryLobbyJpaRepository(InMemoryDatabase database) {
        super(
                database,
                TableDefinition.<UUID, LobbyJpaEntity>of("lobby", LobbyJpaEntity::getId)
                        .unique("lobby_join_code_idx", LobbyJpaEntity::getJoinCode));
    }

    @Override
    public Optional<LobbyJpaEntity> findById(UUID id) {
        return super.findById(id).map(InMemoryLobbyJpaRepository::inJoinOrder);
    }

    @Override
    public Optional<LobbyJpaEntity> findByIdWithLock(UUID id) {
        return findByIdForUpdate(id).map(InMemoryLobbyJpaRepository::inJoinOrder);
    }

    @Override
    public Optional<LobbyJpaEntity> findByJoinCode(String joinCode) {
        return findFirst(lobby -> lobby.getJoinCode().equals(joinCode))
                .map(InMemoryLobbyJpaRepository::inJoinOrder);
    }

    private static LobbyJpaEntity inJoinOrder(LobbyJpaEntity lobby) {
        lobby.getPlayers().sort(JOIN_ORDER);
        return lobby;
    }
}
