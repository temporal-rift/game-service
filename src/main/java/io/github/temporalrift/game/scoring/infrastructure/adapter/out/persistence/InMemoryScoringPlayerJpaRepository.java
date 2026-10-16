package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition.key;

import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryScoringPlayerJpaRepository extends InMemoryJpaRepository<ScoringPlayerJpaEntity, UUID>
        implements ScoringPlayerJpaRepository {

    InMemoryScoringPlayerJpaRepository(InMemoryDatabase database) {
        super(
                database,
                TableDefinition.<UUID, ScoringPlayerJpaEntity>of("scoring_player", ScoringPlayerJpaEntity::getId)
                        .unique("game_player", player -> key(player.getGameId(), player.getPlayerId())));
    }

    @Override
    public boolean existsByGameIdAndPlayerId(UUID gameId, UUID playerId) {
        return findFirst(player -> player.getGameId().equals(gameId)
                        && player.getPlayerId().equals(playerId))
                .isPresent();
    }

    @Override
    public List<ScoringPlayerJpaEntity> findAllByGameId(UUID gameId) {
        return findMatching(player -> player.getGameId().equals(gameId));
    }

    @Override
    public void upsert(UUID id, UUID gameId, UUID playerId, String playerName) {
        lockForUpsert(key("scoring_player", gameId, playerId));
        var player = findFirst(row ->
                        row.getGameId().equals(gameId) && row.getPlayerId().equals(playerId))
                .orElseGet(() -> {
                    var created = new ScoringPlayerJpaEntity();
                    created.setId(id);
                    created.setGameId(gameId);
                    created.setPlayerId(playerId);
                    return created;
                });
        player.setPlayerName(playerName);
        save(player);
    }
}
