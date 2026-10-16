package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.UuidOrder;

@InMemoryRepository
class InMemoryPlayerStateJpaRepository extends InMemoryJpaRepository<PlayerStateJpaEntity, UUID>
        implements PlayerStateJpaRepository {

    InMemoryPlayerStateJpaRepository(InMemoryDatabase database) {
        super(
                database,
                TableDefinition.<UUID, PlayerStateJpaEntity>of("player_state", PlayerStateJpaEntity::getId)
                        .unique("game_player", state -> List.of(state.getGameId(), state.getPlayerId())));
    }

    @Override
    public Optional<PlayerStateJpaEntity> findByGameIdAndPlayerId(UUID gameId, UUID playerId) {
        return findFirst(
                state -> state.getGameId().equals(gameId) && state.getPlayerId().equals(playerId));
    }

    @Override
    public int insertIfAbsent(UUID id, UUID gameId, UUID playerId) {
        var state = new PlayerStateJpaEntity();
        state.setId(id);
        state.setGameId(gameId);
        state.setPlayerId(playerId);
        state.setJammed(false);
        state.setObscured(false);
        state.setHand(new ArrayList<>());
        return insertRowIfAbsent(state) ? 1 : 0;
    }

    @Override
    public Optional<PlayerStateJpaEntity> findByGameIdAndPlayerIdWithLock(UUID gameId, UUID playerId) {
        return findFirstForUpdate(
                state -> state.getGameId().equals(gameId) && state.getPlayerId().equals(playerId));
    }

    @Override
    public List<PlayerStateJpaEntity> findAllByGameIdWithLock(UUID gameId) {
        return findMatchingForUpdate(
                state -> state.getGameId().equals(gameId),
                Comparator.comparing(PlayerStateJpaEntity::getPlayerId, UuidOrder.POSTGRES));
    }

    @Override
    public List<PlayerStateJpaEntity> findAllByGameId(UUID gameId) {
        return findMatching(state -> state.getGameId().equals(gameId));
    }
}
